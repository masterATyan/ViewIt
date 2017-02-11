package com.linroid.viewit.ui.gallery.provider

import android.content.pm.ApplicationInfo
import android.view.View
import com.linroid.viewit.data.model.CloudFavorite
import com.linroid.viewit.data.model.ImageTree
import com.linroid.viewit.data.repo.ScanRepo
import com.linroid.viewit.ui.gallery.GalleryActivity

/**
 * @author linroid <linroid@gmail.com>
 * @since 07/02/2017
 */
class CloudFavoriteViewProvider(activity: GalleryActivity, appInfo: ApplicationInfo, scanRepo: ScanRepo)
    : TreeViewProvider<CloudFavorite>(activity, appInfo, scanRepo) {
    override fun onClick(it: View, data: CloudFavorite) {
        activity.visitCloudFavorite(data)
    }

    override fun obtainTree(data: CloudFavorite): ImageTree? {
        return data.tree
    }

    override fun obtainName(data: CloudFavorite): CharSequence = data.name
}