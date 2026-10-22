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
 * 传的必须是**这张照片自己的标识**（[com.freshnow.app.ui.component.EditPhoto.id] 或详情页的下标），
 * 不能是「第几格」：设首图会交换格子位置，键若按下标给，重排那一帧同一个键就从一张图转到了
 * 另一张上，框架把它当成一次共享元素飞行——用户看到的是图闪一下。
 */
internal fun photoKey(owner: Any): String = "recordPhoto-$owner"

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
 * [visible] 一定要跟着开关走，不能恒为 true：关闭那一帧全屏与缩略图会同时在组合里
 * （退出动画还没播完），两边都自称可见时，框架不知道这笔图到底在哪一份手上，
 * 表现就是图在两块矩形之间乱闪、然后才淡掉。全屏这一侧让出（false）、缩略图接过（true），
 * 图才会从全屏那块矩形飞回格子。
 *
 * 进出的显隐（`AnimatedVisibility`）留在调用方：详情页的看图页与编辑页的看图页挂在不同的
 * 容器里，用同一个入场动画即可，但「什么时候显示」只有各页自己知道。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.PhotoViewerPage(
    image: Bitmap,
    key: String,
    visible: Boolean,
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
                visible = visible,
                boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
            )
        )
    }
}
