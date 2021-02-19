package com.pretty.library.banner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class BannerAdapter<Banner>(
    private val layoutId: Int = 0,
    private var bannerList: ArrayList<Banner> = arrayListOf()
) : RecyclerView.Adapter<BannerLayoutHolder>() {

    private var viewTypeFun: ((position: Int) -> Int)? = null
    private var layoutIdFun: ((viewType: Int) -> Int)? = null
    private var onItemClickFun: ((view: View, position: Int) -> Unit)? = null
    private var bindItemDataFun: ((holder: BannerLayoutHolder, data: Banner, viewType: Int) -> Unit)? = null

    override fun getItemCount(): Int {
        return bannerList.size
    }

    override fun getItemViewType(position: Int): Int {
        return viewTypeFun?.invoke(position) ?: super.getItemViewType(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerLayoutHolder {
        val realLayoutId = layoutIdFun?.invoke(viewType) ?: layoutId
        check(realLayoutId != 0) { "layoutId is null" }
        val view = LayoutInflater.from(parent.context).inflate(realLayoutId, parent, false)
        return BannerLayoutHolder(view)
    }

    override fun onBindViewHolder(holder: BannerLayoutHolder, position: Int) {
        onItemClickFun?.run {
            holder.itemView.setOnClickListener { view ->
                invoke(view, position)
            }
        }
        bindItemDataFun?.invoke(holder, bannerList[position], holder.itemViewType)
    }

    /**
     * 获取指定位置item的类型
     * @author Arvin.xun
     */
    fun viewType(func: (position: Int) -> Int) = apply {
        this.viewTypeFun = func
    }

    /**
     * 根据viewType获取 layoutId
     * @author Arvin.xun
     */
    fun layoutId(func: (viewType: Int) -> Int) = apply {
        this.layoutIdFun = func
    }

    fun onItemClick(func: (view: View, position: Int) -> Unit) = apply {
        this.onItemClickFun = func
    }

    /**
     * 绑定item数据
     * @author Arvin.xun
     */
    fun bindItemData(func: (holder: BannerLayoutHolder, data: Banner, viewType: Int) -> Unit) = apply {
        this.bindItemDataFun = func
    }

    /**
     * 设置banner数据
     * @author Arvin.xun
     */
    fun setBannerList(list: ArrayList<Banner>?) = apply {
        this.bannerList = list ?: arrayListOf()
        notifyDataSetChanged()
    }

    /**
     * 获取所有Banner
     * @author Arvin.xun
     */
    fun getBannerList() = bannerList

    /**
     * 获取指定位置得Bananer
     * @author Arvin.xun
     */
    fun getBanner(position: Int) = bannerList[position]

}