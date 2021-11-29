package com.ducdiep.map.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.ducdiep.map.R
import com.here.android.mpa.search.AutoSuggest
import com.here.android.mpa.search.AutoSuggestPlace
import com.here.android.mpa.search.AutoSuggestQuery
import com.here.android.mpa.search.AutoSuggestSearch

class AutoSuggestAdapter(var context:Context, var listSuggest:List<AutoSuggest>):RecyclerView.Adapter<AutoSuggestAdapter.SuggestViewHolder>() {
    var onClick:((AutoSuggest)->Unit)? = null

    fun setOnClickItem(callBack:(AutoSuggest)->Unit){
        onClick = callBack
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SuggestViewHolder {
        var view = LayoutInflater.from(context).inflate(R.layout.auto_suggest_item,parent,false)
        return SuggestViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestViewHolder, position: Int) {
        var autoSuggest = listSuggest[position]
        holder.title.text = HtmlCompat.fromHtml(autoSuggest.title, HtmlCompat.FROM_HTML_MODE_LEGACY)

        when (autoSuggest.type) {
            AutoSuggest.Type.PLACE -> {
                val autoSuggestPlace = autoSuggest as AutoSuggestPlace
                if (autoSuggestPlace.vicinity != null) {
                    holder.vicinity.text =
                    HtmlCompat.fromHtml("Địa chỉ: " + autoSuggestPlace.vicinity, HtmlCompat.FROM_HTML_MODE_LEGACY)
                } else {
                    holder.vicinity.text = "Địa chỉ: Không xác định"
                }
            }
            AutoSuggest.Type.QUERY -> {
                val autoSuggestQuery = autoSuggest as AutoSuggestQuery
                if (autoSuggestQuery.queryCompletion != null) {
                    holder.vicinity.text =
                    HtmlCompat.fromHtml("Địa chỉ: " + autoSuggestQuery.queryCompletion, HtmlCompat.FROM_HTML_MODE_LEGACY)

                } else {
                    holder.vicinity.text = "Địa chỉ: Không xác định"
                }
            }
            AutoSuggest.Type.SEARCH -> {
                val autoSuggestSearch = autoSuggest as AutoSuggestSearch
                holder.vicinity.visibility = View.GONE
            }
            else -> {
            }
        }



        holder.itemView.setOnClickListener{
            onClick?.invoke(autoSuggest)
        }

    }

    override fun getItemCount(): Int {
        return listSuggest.size
    }

    class SuggestViewHolder(itemView: View) :RecyclerView.ViewHolder(itemView){
        var title = itemView.findViewById<TextView>(R.id.title)
        var vicinity = itemView.findViewById<TextView>(R.id.vicinity)
    }
}