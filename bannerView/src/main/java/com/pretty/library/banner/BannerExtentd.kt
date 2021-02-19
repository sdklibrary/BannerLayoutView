package com.pretty.library.banner


inline fun <T : Any> createBannerAdapter(
    layoutId: Int? = null,
    listData: ArrayList<T>? = null,
    init: BannerAdapter<T>.() -> Unit
): BannerAdapter<T> {
    val adapter = BannerAdapter<T>()
    layoutId?.let {
        adapter.layoutId {
            layoutId
        }
    }
    listData?.let {
        adapter.setBannerList(it)
    }
    init.invoke(adapter)
    return adapter
}

