import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

function run(cmd, args, options = {}) {
  return execFileSync(cmd, args, {
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
    ...options,
  });
}

const repo = process.env.GITHUB_REPOSITORY;
const branch = process.env.BRANCH_NAME || 'main';
const message = process.env.COMMIT_MESSAGE || 'chore(website): auto-sync wiki & docs/ build';

if (!repo) {
  throw new Error('GITHUB_REPOSITORY environment variable is required.');
}

run('git', [
  'add',
  '-A',
  '--',
  'docs/',
  'website/src/data/wiki/',
  'website/public/',
  'gitbook/',
  '.github/assets/github-sponsors-button.svg',
  'README.md',
]);

const diffRaw = execFileSync('git', ['diff', '--cached', '--no-renames', '--name-status', '-z']);
if (diffRaw.length === 0) {
  console.log('No changes to commit.');
  process.exit(0);
}

const tokens = diffRaw.toString('utf8').split('\0').filter((t) => t.length > 0);
const changes = [];
for (let i = 0; i < tokens.length; i += 2) {
  const status = tokens[i];
  const filePath = tokens[i + 1];
  if (!filePath) continue;
  if (status === 'D') {
    changes.push({ kind: 'delete', path: filePath });
  } else {
    changes.push({
      kind: 'add',
      path: filePath,
      contents: readFileSync(filePath).toString('base64'),
    });
  }
}

console.log(`Found ${changes.length} staged change(s) for Verified GraphQL commit on ${branch}.`);

let expectedHeadOid = run('git', ['rev-parse', 'HEAD']).trim();
const remoteOid = run('gh', ['api', `repos/${repo}/git/ref/heads/${branch}`, '-q', '.object.sha']).trim();
if (remoteOid && remoteOid !== expectedHeadOid) {
  console.log(`Remote ${branch} moved (${expectedHeadOid} -> ${remoteOid}); rebasing staged changes.`);
  run('git', ['-c', 'user.name=github-actions[bot]', '-c', 'user.email=41898282+github-actions[bot]@users.noreply.github.com', 'commit', '-m', 'temp-sync']);
  run('git', ['fetch', 'origin', branch]);
  run('git', ['rebase', '-X', 'ours', `origin/${branch}`]);
  run('git', ['reset', '--soft', `origin/${branch}`]);
  expectedHeadOid = remoteOid;
}

const mutation = `
  mutation ($input: CreateCommitOnBranchInput!) {
    createCommitOnBranch(input: $input) {
      commit {
        oid
        url
      }
    }
  }
`;

// Batch file changes conservatively (~60 changes or <= 12 MiB Base64 per mutation)
// to stay well within GitHub GraphQL request size and complexity limits.
const MAX_CHANGES_PER_BATCH = 60;
const MAX_BYTES_PER_BATCH = 12 * 1024 * 1024;
const batches = [];
let currentBatch = [];
let currentBytes = 0;

for (const change of changes) {
  const changeBytes = change.contents ? change.contents.length : change.path.length;
  if (
    currentBatch.length > 0 &&
    (currentBatch.length >= MAX_CHANGES_PER_BATCH || currentBytes + changeBytes > MAX_BYTES_PER_BATCH)
  ) {
    batches.push(currentBatch);
    currentBatch = [];
    currentBytes = 0;
  }
  currentBatch.push(change);
  currentBytes += changeBytes;
}
if (currentBatch.length > 0) {
  batches.push(currentBatch);
}

for (let idx = 0; idx < batches.length; idx += 1) {
  const batch = batches[idx];
  const additions = batch.filter((c) => c.kind === 'add').map(({ path, contents }) => ({ path, contents }));
  const deletions = batch.filter((c) => c.kind === 'delete').map(({ path }) => ({ path }));
  const headline = batches.length === 1 ? message : `${message} (${idx + 1}/${batches.length})`;

  const payload = {
    query: mutation,
    variables: {
      input: {
        branch: {
          repositoryNameWithOwner: repo,
          branchName: branch,
        },
        expectedHeadOid,
        message: { headline },
        fileChanges: {
          ...(additions.length > 0 ? { additions } : {}),
          ...(deletions.length > 0 ? { deletions } : {}),
        },
      },
    },
  };

  const out = run('gh', ['api', 'graphql', '--input', '-'], {
    input: JSON.stringify(payload),
  });
  const parsed = JSON.parse(out);
  if (parsed.errors?.length) {
    throw new Error(`GraphQL createCommitOnBranch failed: ${JSON.stringify(parsed.errors)}`);
  }
  const created = parsed.data?.createCommitOnBranch?.commit;
  if (!created?.oid) {
    throw new Error(`Unexpected GraphQL response: ${out}`);
  }
  expectedHeadOid = created.oid;
  console.log(`Created Verified commit ${created.oid}: ${created.url}`);
}
