package com.linroid.viewit.ui.gallery.provider

import android.content.pm.ApplicationInfo
import android.view.View
import com.linroid.viewit.data.ImageRepo
import com.linroid.viewit.data.model.ImageTree
import com.linroid.viewit.data.model.Recommendation
import com.linroid.viewit.ui.gallery.GalleryActivity

/**
 * @author linroid <linroid@gmail.com>
 * @since 07/02/2017
 */
class RecommendationViewProvider(activity: GalleryActivity, appInfo: ApplicationInfo, imageRepo: ImageRepo)
    : TreeViewProvider<Recommendation>(activity, appInfo, imageRepo) {
    override fun onClick(it: View, data: Recommendation) {
        activity.visitRecommendation(data)
    }

    override fun obtainTree(data: Recommendation): ImageTree? {
        return data.tree
    }

    override fun obtainName(data: Recommendation): CharSequence = data.name
}