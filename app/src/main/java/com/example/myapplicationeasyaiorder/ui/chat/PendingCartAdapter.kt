package com.example.myapplicationeasyaiorder.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplicationeasyaiorder.databinding.ItemPendingCartBinding

data class PendingCartItem(
    val productId: String,
    val name: String,
    val price: Double,
    var quantity: Int,
    val imageUrl: String? = null
)

class PendingCartAdapter(
    private val onQuantityChange: (PendingCartItem, Int) -> Unit,
    private val onRemove: (PendingCartItem) -> Unit
) : ListAdapter<PendingCartItem, PendingCartAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingCartBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPendingCartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PendingCartItem) {
            binding.itemName.text = item.name
            binding.itemPrice.text = "$${String.format("%.2f", item.price)}"
            binding.quantityText.text = item.quantity.toString()

            binding.btnIncrease.setOnClickListener {
                onQuantityChange(item, item.quantity + 1)
            }

            binding.btnDecrease.setOnClickListener {
                if (item.quantity > 1) {
                    onQuantityChange(item, item.quantity - 1)
                }
            }

            binding.btnRemove.setOnClickListener {
                onRemove(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PendingCartItem>() {
        override fun areItemsTheSame(oldItem: PendingCartItem, newItem: PendingCartItem): Boolean {
            return oldItem.productId == newItem.productId
        }

        override fun areContentsTheSame(oldItem: PendingCartItem, newItem: PendingCartItem): Boolean {
            return oldItem == newItem
        }
    }
}
