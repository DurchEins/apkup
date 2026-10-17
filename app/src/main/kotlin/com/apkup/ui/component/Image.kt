package com.apkup.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.graphics.drawable.Drawable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.apkup.R
import com.apkup.util.getAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun BaseLoadingImage(
    request: ImageRequest,
    modifier: Modifier,
    color: Color = Color.Transparent
) = AsyncImage(
    model = request,
    contentDescription = stringResource(R.string.app_cd),
    modifier = modifier
        .padding(10.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(color),
    contentScale = ContentScale.Fit,
    error = painterResource(R.drawable.ic_root),
    placeholder = painterResource(R.drawable.ic_empty)
)

@Composable
fun LoadingImageApp(
    packageName: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent
) {
    val context = LocalContext.current
    val icon = produceState<Drawable?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { context.getAppIcon(packageName) }
    }.value
    val request = ImageRequest.Builder(context).data(icon).build()
    BaseLoadingImage(request, modifier, color)
}
