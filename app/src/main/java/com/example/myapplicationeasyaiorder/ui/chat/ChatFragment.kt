package com.example.myapplicationeasyaiorder.ui.chat

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplicationeasyaiorder.R
import com.example.myapplicationeasyaiorder.databinding.FragmentChatBinding
import com.example.myapplicationeasyaiorder.ui.EasyOrderViewModelFactory

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels {
        EasyOrderViewModelFactory(requireContext())
    }

    private val chatAdapter = ChatAdapter()
    private var confirmDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text?.toString() ?: ""
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.messageInput.setText("")
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    binding.chatRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Loading indicator removed from UI as per request
        }

        // Observe pending items to show confirmation dialog
        viewModel.pendingItems.observe(viewLifecycleOwner) { items ->
            if (items != null && items.isNotEmpty()) {
                showConfirmDialog(items, viewModel.unavailableItems.value ?: emptyList())
            }
        }
    }

    private fun showConfirmDialog(items: List<PendingCartItem>, unavailable: List<String>) {
        confirmDialog?.dismiss()
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_items, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.pendingItemsRecyclerView)
        
        val adapter = PendingCartAdapter(
            onQuantityChange = { item, newQty ->
                viewModel.updatePendingItemQuantity(item.productId, newQty)
            },
            onRemove = { item ->
                viewModel.removePendingItem(item.productId)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.submitList(items.toList())
        
        confirmDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            viewModel.cancelPendingItems()
            confirmDialog?.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.confirmButton).setOnClickListener {
            viewModel.confirmPendingItems()
            confirmDialog?.dismiss()
        }
        
        // Update adapter when pending items change
        viewModel.pendingItems.observe(viewLifecycleOwner) { updatedItems ->
            if (updatedItems == null || updatedItems.isEmpty()) {
                confirmDialog?.dismiss()
            } else {
                adapter.submitList(updatedItems.toList())
            }
        }
        
        confirmDialog?.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        confirmDialog?.dismiss()
        _binding = null
    }
}
