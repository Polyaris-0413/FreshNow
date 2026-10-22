package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowTransitions

/**
 * 照片缩略图与全屏看图之间共享元素的键。
 *
 * 一条记录可以有九张照片，所以键要带上「第几张」：同一个键挂在两个节点上时，框架认不出谁飞给谁
 * （见 [photoThumbnail]）。
 */
internal fun photoKey(index: Int): String = "recordPhoto-$index"

/**
 * 缩略图一侧的共享元素。详情页的主图、编辑页与扫描页的格子里都用它。
 *
 * [visible] 由调用方管：看图打开期间缩略图这一侧隐形，图交给全屏那一份；关闭时再由它把图收回来
 * （两边的矩形不同，过渡就是那块矩形在飞）。调用方自己不去做 `visible` 的换算，三处才不会各算各的。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.photoThumbnail(
    key: String,
    visible: Boolean
): Modifier = Modifier.sharedElementWithCallerManagedVisibility(
    sharedContentState = rememberSharedContentState(key = key),
    visible = visible,
    boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
)

/**
 * 全屏看图那一页：标题、返回箭头、手势与共享元素都接好，调用方只管什么时候把它放进组合。
 *
 * 抽出来是因为它与缩略图那一侧必须用同一个键、同一套过渡参数：两处各写一遍的话，
 * 某天只想改一边的动画，改完就是「飞进去很顺、飞回来是硬切」。
 *
 * 进出的显隐（`AnimatedVisibility`）留在调用方：详情页的看图页与编辑页的看图页挂在不同的
 * 容器里，用同一个入场动画即可，但「什么时候显示」只有各页自己知道。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.PhotoViewerPage(
    image: Bitmap,
    key: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    FreshNowSubPage(
        title = stringResource(R.string.record_image),
        onBack = onDismiss,
        modifier = modifier
    ) { innerPadding ->
        PhotoViewer(
            image = image,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            imageModifier = Modifier.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(key = key),
                visible = true,
                boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
            )
        )
    }
}
