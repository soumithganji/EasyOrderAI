package com.example.myapplicationeasyaiorder.ui.cart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplicationeasyaiorder.data.LocalCartRepository
import com.example.myapplicationeasyaiorder.databinding.FragmentCartBinding
import com.example.myapplicationeasyaiorder.ui.cart.LocalCartAdapter
import com.example.myapplicationeasyaiorder.ui.cart.CartViewModel

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartAdapter: LocalCartAdapter

    private val viewModel: CartViewModel by viewModels {
        com.example.myapplicationeasyaiorder.ui.EasyOrderViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cartAdapter = LocalCartAdapter()

        binding.cartRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = cartAdapter
            visibility = View.VISIBLE
        }

        LocalCartRepository.cartItems.observe(viewLifecycleOwner) { items ->
            if (items.isEmpty()) {
                binding.cartRecyclerView.visibility = View.GONE
                binding.infoLayout.visibility = View.VISIBLE
            } else {
                binding.cartRecyclerView.visibility = View.VISIBLE
                binding.infoLayout.visibility = View.GONE
                cartAdapter.submitList(items)
            }
        }

        binding.openKrogerButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kroger.com/cart"))
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
