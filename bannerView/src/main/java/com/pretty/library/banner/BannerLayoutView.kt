package com.pretty.library.banner

import android.content.Context
import android.graphics.Outline
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.pretty.library.indicator.Indicator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BannerLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private var lastX = 0f
    private var lastY = 0f
    private var startX = 0f
    private var startY = 0f
    private var sidePage = 1
    private var tempPosition = 0
    private var placeholderPage = 2
    private var isLooper: Boolean = true
    private var isAutoPlay: Boolean = true
    private var pageScrollTime: Long = 600
    private var pageIntervalTime: Long = 2500
    private var isBeginPagerChange: Boolean = true
    private var isRegisteredAdapter: Boolean = false
    private var indicator: Indicator? = null
    private val viewPager = ViewPager2(context)
    private val bannerAdapter = BannerWrapperAdapter()
    private val compositePageTransformer = CompositePageTransformer()
    private var changeCallback: ViewPager2.OnPageChangeCallback? = null
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop shr 1

    init {
        viewPager.adapter = bannerAdapter
        viewPager.setPageTransformer(compositePageTransformer)
        viewPager.registerOnPageChangeCallback(OnPageChangeCallback())
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BannerLayoutView)
        setAutoPlay(typedArray.getBoolean(R.styleable.BannerLayoutView_bannerAutoPlay, true))
        setLooperEnable(typedArray.getBoolean(R.styleable.BannerLayoutView_bannerLooper, true))
        setPagerScrollTime(typedArray.getInt(R.styleable.BannerLayoutView_bannerTimeScroll, 600).toLong())
        setIntervalTime(typedArray.getInt(R.styleable.BannerLayoutView_bannerTimeInterval, 2500).toLong())
        setOrientation(typedArray.getInt(R.styleable.BannerLayoutView_bannerOrientation, 0))
        typedArray.recycle()
        setOffscreenPageLimit(1)
        initViewPagerScrollProxy()
        addView(viewPager, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTurning()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTurning()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE)
            startTurning()
        else
            stopTurning()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isAutoPlay() && viewPager.isUserInputEnabled) {
            val action = ev.action
            if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_OUTSIDE
            ) {
                startTurning()
            } else if (action == MotionEvent.ACTION_DOWN)
                stopTurning()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        if (action == MotionEvent.ACTION_DOWN) {
            lastX = ev.rawX
            startX = lastX
            lastY = ev.rawY
            startY = lastY
        } else if (action == MotionEvent.ACTION_MOVE) {
            lastX = ev.rawX
            lastY = ev.rawY
            if (viewPager.isUserInputEnabled) {
                val distanceX = abs(lastX - startX)
                val distanceY = abs(lastY - startY)
                val disallowIntercept: Boolean
                disallowIntercept = if (viewPager.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
                    distanceX > scaledTouchSlop && distanceX > distanceY
                } else {
                    distanceY > scaledTouchSlop && distanceY > distanceX
                }
                parent.requestDisallowInterceptTouchEvent(disallowIntercept)
            }
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            return abs(lastX - startX) > scaledTouchSlop || abs(lastY - startY) > scaledTouchSlop
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * 初始化位置和初始化指示器
     */
    private fun startPager(startPosition: Int) {
        tempPosition = if (isLooper)
            startPosition + sidePage
        else
            startPosition
        bannerAdapter.notifyDataSetChanged()
        indicator?.initIndicatorCount(realCount())
        viewPager.setCurrentItem(tempPosition, false)
        startTurning()
    }

    /**
     * 真实得数据条数
     */
    private fun realCount(): Int {
        return bannerAdapter.realCount()
    }

    /**
     * 真实得位置
     */
    private fun toRealPosition(position: Int): Int {
        return if (isLooper) {
            var realPosition = 0
            if (realCount() > 1)
                realPosition = (position - sidePage) % realCount()
            if (realPosition < 0)
                realPosition += realCount()
            realPosition
        } else
            position
    }

    /**
     * 自动轮播任务
     */
    private val looperTask = object : Runnable {
        override fun run() {
            if (isAutoPlay()) {
                tempPosition++
                if (isLooper) {
                    if (tempPosition == realCount() + sidePage + 1) {
                        isBeginPagerChange = false
                        viewPager.setCurrentItem(sidePage, false)
                        post(this)
                    } else {
                        isBeginPagerChange = true
                        viewPager.currentItem = tempPosition
                        postDelayed(this, pageIntervalTime)
                    }
                } else {
                    if (tempPosition >= realCount()) {
                        isBeginPagerChange = false
                        viewPager.setCurrentItem(--tempPosition, false)
                        stopTurning()
                    } else {
                        isBeginPagerChange = true
                        viewPager.currentItem = tempPosition
                        postDelayed(this, pageIntervalTime)
                    }
                }
            }
        }
    }

    /**
     * ViewPager 滑动代理
     */
    private fun initViewPagerScrollProxy() {
        try {
            val viewPagerClass = ViewPager2::class.java
            val recyclerView = viewPager.getChildAt(0) as RecyclerView
            recyclerView.overScrollMode = OVER_SCROLL_NEVER
            val proxyLayoutManger = ProxyLayoutManger(context, viewPager.orientation)
            recyclerView.layoutManager = proxyLayoutManger
            val layoutManagerField = viewPagerClass.getDeclaredField("mLayoutManager")
            layoutManagerField.isAccessible = true
            layoutManagerField[viewPager] = proxyLayoutManger
            val pageTransformerAdapterField = viewPagerClass.getDeclaredField("mPageTransformerAdapter")
            pageTransformerAdapterField.isAccessible = true
            val pageTransformerAdapter = pageTransformerAdapterField[viewPager]
            if (pageTransformerAdapter != null) {
                val aClass: Class<*> = pageTransformerAdapter.javaClass
                val layoutManager = aClass.getDeclaredField("mLayoutManager")
                layoutManager.isAccessible = true
                layoutManager[pageTransformerAdapter] = proxyLayoutManger
            }

            val scrollEventAdapterField = viewPagerClass.getDeclaredField("mScrollEventAdapter")
            scrollEventAdapterField.isAccessible = true
            val scrollEventAdapter = scrollEventAdapterField[viewPager]
            if (scrollEventAdapter != null) {
                val aClass: Class<*> = scrollEventAdapter.javaClass
                val layoutManager = aClass.getDeclaredField("mLayoutManager")
                layoutManager.isAccessible = true
                layoutManager[scrollEventAdapter] = proxyLayoutManger
            }
        } catch (e: Exception) {
            Log.i("BannerLayout", e.message ?: e.toString())
        }
    }

    /**
     * Adapter 数据变化订阅
     */
    private val itemDataSetChangeObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            onChanged()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            onChanged()
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            onChanged()
        }

        override fun onChanged() {
            startPager(currentPage())
        }
    }

    /**
     * 页面变化回调
     */
    private inner class OnPageChangeCallback : ViewPager2.OnPageChangeCallback() {

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            val realPosition = toRealPosition(position)
            changeCallback?.onPageScrolled(realPosition, positionOffset, positionOffsetPixels)
            indicator?.onPageScrolled(realPosition, positionOffset, positionOffsetPixels)
        }

        override fun onPageSelected(position: Int) {
            if (realCount() > 1)
                tempPosition = position
            if (isBeginPagerChange) {
                val realPosition = toRealPosition(position)
                changeCallback?.onPageSelected(realPosition)
                indicator?.onPageSelected(realPosition)
            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                if (isLooper) {
                    when (tempPosition) {
                        sidePage - 1 -> {
                            isBeginPagerChange = false
                            viewPager.setCurrentItem(realCount() + tempPosition, false)
                        }
                        realCount() + sidePage -> {
                            isBeginPagerChange = false
                            viewPager.setCurrentItem(sidePage, false)
                        }
                        else -> isBeginPagerChange = true
                    }
                } else
                    isBeginPagerChange = true
            }
            changeCallback?.onPageScrollStateChanged(state)
            indicator?.onPageScrollStateChanged(state)
        }
    }

    /**
     * Adapter
     */
    private inner class BannerWrapperAdapter : RecyclerView.Adapter<BannerLayoutHolder>() {

        private var adapter: RecyclerView.Adapter<BannerLayoutHolder>? = null

        fun realCount(): Int {
            return adapter?.itemCount ?: 0
        }

        override fun getItemCount(): Int {
            return if (realCount() > 1 && isLooper) realCount() + placeholderPage else realCount()
        }

        override fun getItemId(position: Int): Long {
            return adapter!!.getItemId(toRealPosition(position))
        }

        override fun getItemViewType(position: Int): Int {
            return adapter!!.getItemViewType(toRealPosition(position))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerLayoutHolder {
            return adapter!!.onCreateViewHolder(parent, viewType)
        }

        override fun onBindViewHolder(holder: BannerLayoutHolder, position: Int) {
            adapter!!.onBindViewHolder(holder, toRealPosition(position))
        }

        fun registerAdapter(adapter: RecyclerView.Adapter<BannerLayoutHolder>) {
            this.adapter?.unregisterAdapterDataObserver(itemDataSetChangeObserver)
            this.adapter = adapter
            this.adapter?.registerAdapterDataObserver(itemDataSetChangeObserver)
        }
    }

    private inner class ProxyLayoutManger constructor(context: Context?, orientation: Int) :
        LinearLayoutManager(context, orientation, false) {
        override fun smoothScrollToPosition(
            recyclerView: RecyclerView,
            state: RecyclerView.State,
            position: Int
        ) {
            val linearSmoothScroller: LinearSmoothScroller =
                object : LinearSmoothScroller(recyclerView.context) {
                    override fun calculateTimeForDeceleration(dx: Int): Int {
                        return (pageScrollTime * (1 - 0.3356)).toInt()
                    }
                }
            linearSmoothScroller.targetPosition = position
            startSmoothScroll(linearSmoothScroller)
        }
    }

    /*-------------------------下面是对外的函数----------------------------------- */

    /**
     * 设置自动轮播
     * @author Arvin.xun
     * @param autoPlay 是否自动轮播
     */
    fun setAutoPlay(autoPlay: Boolean) = apply {
        this.isAutoPlay = autoPlay
        if (isAutoPlay && realCount() > 1)
            startTurning()
    }

    /**
     * 设置是否轮播
     * @author Arvin.xun
     */
    fun setLooperEnable(enable: Boolean) = apply {
        this.isLooper = enable
    }

    /**
     * 设置Banner得方向
     * @author Arvin.xun
     * @param orientation Orientation.ORIENTATION_HORIZONTAL or Orientation.ORIENTATION_VERTICAL
     */
    fun setOrientation(@ViewPager2.Orientation orientation: Int) = apply {
        viewPager.orientation = orientation
    }

    /**
     * 设置一屏多页
     * @author Arvin.xun
     * @param pageMargin pager与pager之间的宽度
     * @param lWidth    左边页面露出来的宽度
     * @param rWidth    右边页面露出来的宽度
     */
    fun setPageMargin(pageMargin: Int = 0, lWidth: Int = 0, rWidth: Int = lWidth) = apply {
        val margin = if (pageMargin < 0) 0 else pageMargin
        addPageTransformer(MarginPageTransformer(margin))
        val recyclerView = viewPager.getChildAt(0) as RecyclerView
        if (viewPager.orientation == ViewPager2.ORIENTATION_VERTICAL) {
            recyclerView.setPadding(
                viewPager.paddingLeft,
                lWidth + abs(margin),
                viewPager.paddingRight,
                rWidth + abs(margin)
            )
        } else {
            recyclerView.setPadding(
                lWidth + abs(margin),
                viewPager.paddingTop,
                rWidth + abs(margin),
                viewPager.paddingBottom
            )
        }
        recyclerView.clipToPadding = false
        placeholderPage = 4
        sidePage = 2
    }

    /**
     * 设置Banner自动轮播间隔时长
     * @author Arvin.xun
     * @param intervalTime 间隔时长
     */
    fun setIntervalTime(intervalTime: Long) = apply {
        this.pageIntervalTime = intervalTime
    }

    /**
     * 设置Banner的切换时长
     * @author Arvin.xun
     * @param scrollTime 切换时长
     */
    fun setPagerScrollTime(scrollTime: Long) = apply {
        this.pageScrollTime = scrollTime
    }

    /**
     * 设置Banner预加载数
     * @author Arvin.xun
     * @param limit 预加载数
     */
    fun setOffscreenPageLimit(limit: Int) = apply {
        viewPager.offscreenPageLimit = limit
    }

    /**
     * 设置banner 改变监听
     * @author Arvin.xun
     * @param listener
     */
    fun setBannerChangeListener(listener: ViewPager2.OnPageChangeCallback) = apply {
        changeCallback = listener
    }

    /**
     * 设置指示器
     * @author Arvin.xun
     *
     */
    fun setIndicator(indicator: Indicator, attachToRoot: Boolean = true) = apply {
        if (this.indicator != null)
            removeView(this.indicator!!.getView())
        this.indicator = indicator
        if (attachToRoot) {
            if (indicator is View && indicator.parent != null) {
                val parentView = indicator.parent as ViewGroup
                parentView.removeView(indicator)
            }
            addView(this.indicator!!.getView(), this.indicator!!.params())
        }
    }

    /**
     * 设置Adapter
     * @author Arvin.xun
     */
    fun setBannerAdapter(adapter: RecyclerView.Adapter<BannerLayoutHolder>) {
        isRegisteredAdapter = true
        bannerAdapter.registerAdapter(adapter)
        startPager(0)
    }

    /**
     * 选择某一项
     * @author Arvin.xun
     */
    fun setCurrentItem(position: Int) = apply {
        if (position < 0)
            return@apply
        viewPager.currentItem = if (isLooper)
            position + sidePage
        else {
            if (position > 1)
                min(realCount(), position)
            else
                0
        }
    }

    /**
     * 设置Banner 圆角
     * @author Arvin.xun
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    fun setRoundCorners(radius: Float) = apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
    }

    /**
     * 添加Banner pager滑动动画
     * @author Arvin.xun
     * @param transformer 添加的动画
     */
    fun addPageTransformer(transformer: ViewPager2.PageTransformer) = apply {
        compositePageTransformer.addTransformer(transformer)
    }

    /**
     * 添加Decoration
     * @author Arvin.xun
     * @param decor 添加的Decoration
     * @param index 在Decoration链中插入此装饰的位置。
     */
    fun addItemDecoration(decor: RecyclerView.ItemDecoration, index: Int = -1) = apply {
        viewPager.addItemDecoration(decor, index)
    }

    /**
     * 当前展示的page
     * @author Arvin.xun
     */
    fun currentPage() = max(toRealPosition(tempPosition), 0)

    /**
     * 是否自动滚动
     * @author Arvin.xun
     */
    fun isAutoPlay() = isAutoPlay && realCount() > 1

    /**
     * 开始
     * @author Arvin.xun
     */
    fun startTurning() {
        removeCallbacks(looperTask)
        if (isAutoPlay()) {
            postDelayed(looperTask, pageIntervalTime)
        }
    }

    /**
     * 停止
     * @author Arvin.xun
     */
    fun stopTurning() {
        removeCallbacks(looperTask)
    }

    companion object {

        @JvmStatic
        @BindingAdapter(
            value = ["bannerAdapter", "bannerData"],
            requireAll = false
        )
        fun <T : Any> bindBannerAdapter(
            bannerView: BannerLayoutView,
            adapter: BannerAdapter<T>?,
            listData: ArrayList<T>?
        ) {
            adapter?.let {
                if (!bannerView.isRegisteredAdapter)
                    bannerView.setBannerAdapter(it)
                adapter.setBannerList(listData)
            }
        }
    }
}