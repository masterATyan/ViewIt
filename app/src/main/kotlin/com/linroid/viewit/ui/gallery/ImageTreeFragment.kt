package com.linroid.viewit.ui.gallery

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import butterknife.bindView
import com.linroid.viewit.R
import com.linroid.viewit.data.ImageRepo
import com.linroid.viewit.data.model.Image
import com.linroid.viewit.data.model.ImageTree
import com.linroid.viewit.ui.favorite.FavoriteCreateActivity
import com.linroid.viewit.ui.gallery.provider.Category
import com.linroid.viewit.ui.gallery.provider.CategoryViewProvider
import com.linroid.viewit.ui.gallery.provider.ImageTreeViewProvider
import com.linroid.viewit.ui.gallery.provider.ImageViewProvider
import com.linroid.viewit.utils.ARG_IMAGE_TREE_PATH
import com.linroid.viewit.utils.FormatUtils
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import me.drakeet.multitype.MultiTypeAdapter
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.*
import javax.inject.Inject

/**
 * @author linroid <linroid@gmail.com>
 * @since 30/01/2017
 */
class ImageTreeFragment : GalleryAbstractFragment() {
    val SPAN_COUNT = 4

    companion object {
        fun newInstance(tree: ImageTree): ImageTreeFragment {
            val args = Bundle()
            args.putString(ARG_IMAGE_TREE_PATH, tree.dir)
            val fragment = ImageTreeFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var treePath: String

    @Inject lateinit var imageRepo: ImageRepo
    @Inject lateinit var appInfo: ApplicationInfo
    @Inject lateinit var activity: GalleryActivity

    private val items = ArrayList<Any>()
    private var adapter = MultiTypeAdapter(items)

    private lateinit var treeCategory: Category
    private lateinit var imageCategory: Category


    val recyclerView: RecyclerView by bindView(R.id.recyclerView)
    val dirView: TextView by bindView(R.id.dir)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate:$arguments")
        treePath = arguments!!.getString(ARG_IMAGE_TREE_PATH)!!
        setHasOptionsMenu(true)
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        if (activity is GalleryActivity) {
            activity.graph().inject(this)
        }
    }

    override fun provideLayoutId(): Int = R.layout.fragment_image_tree

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter.register(Image::class.java, ImageViewProvider(activity, imageRepo, appInfo))
        adapter.register(ImageTree::class.java, ImageTreeViewProvider(activity, treePath, appInfo, imageRepo))
        adapter.register(Category::class.java, CategoryViewProvider())
        treeCategory = Category(null, getString(R.string.label_category_tree), items)
        imageCategory = Category(treeCategory, getString(R.string.label_category_tree_images, 0), items)

        val gridLayoutManager = GridLayoutManager(getActivity(), SPAN_COUNT)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (items[position] is Category) SPAN_COUNT else 1
            }

        }
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.setHasFixedSize(true)

        imageRepo.registerTreeBuilder()
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribe {
                    refresh(it.getChildTree(treePath))
                }
    }

    private fun refresh(tree: ImageTree?) {
        dirView.text = FormatUtils.formatPath(tree?.dir, appInfo)
        items.clear()
        if (tree != null) {
            val treeItems = ArrayList<ImageTree>()
            tree.children.forEach { subPath, imageTree ->
                treeItems.add(imageTree.nonEmptyChild())
            }
            treeCategory.apply {
                action = getString(R.string.label_category_action_all_images, tree.allImagesCount())
                actionClickListener = View.OnClickListener { activity.viewImages(tree) }
                items = treeItems
            }
            imageCategory.apply {
                label = getString(R.string.label_category_tree_images, tree.images.size)
                items = tree.images
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_image_tree, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_favorite_create -> {
                FavoriteCreateActivity.navTo(activity, treePath, appInfo)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}