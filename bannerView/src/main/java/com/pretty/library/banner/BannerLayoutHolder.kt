package com.pretty.library.banner

import android.util.SparseArray
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

@Suppress("UNCHECKED_CAST")
class BannerLayoutHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    private val views = SparseArray<View>()
    private var dataBinding: ViewDataBinding? = null

    init {
        try {
            dataBinding = DataBindingUtil.bind(itemView)
        } catch (e: Exception) {
        }
    }

    fun setBindData(
        variableId: Int,
        data: Any
    ) = apply {
        dataBinding?.run {
            setVariable(variableId, data)
        }
    }

    /**
     * @author Arvin.xun
     */
    fun setText(
        @IdRes viewId: Int,
        value: CharSequence = ""
    ) = apply {
        val view = getView<TextView>(viewId)
        view.text = value
    }

    /**
     * @author Arvin.xun
     */
    fun setText(
        @IdRes viewId: Int,
        @StringRes strId: Int
    ) = apply {
        val view = getView<TextView>(viewId)
        view.setText(strId)
    }

    /**
     * @author Arvin.xun
     */
    fun setTextColor(
        @IdRes viewId: Int,
        @ColorInt textColor: Int
    ) = apply {
        val view = getView<TextView>(viewId)
        view.setTextColor(textColor)
    }

    /**
     * @author Arvin.xun
     */
    fun setImageResource(
        @IdRes viewId: Int,
        @DrawableRes imageResId: Int
    ) = apply {
        val view = getView<ImageView>(viewId)
        view.setImageResource(imageResId)
    }

    fun <T : View> getView(@IdRes viewId: Int): T {
        var view: View? = views.get(viewId)
        if (view == null) {
            view = itemView.findViewById(viewId)
            views.put(viewId, view)
        }
        return view as T
    }
}