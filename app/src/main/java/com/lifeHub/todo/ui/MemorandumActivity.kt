package com.lifeHub.todo.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.widget.EditText
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lifeHub.R
import com.lifeHub.databinding.ActivityMemorandumBinding
import com.lifeHub.main.ui.MainPage
import com.lifeHub.todo.viewmodel.MemoViewModel
import com.lifeHub.todo.viewmodel.MemoViewModelFactory


class MemorandumActivity : com.lifeHub.login.AccountActivity() {

    private lateinit var binding: ActivityMemorandumBinding
    private lateinit var viewModel: MemoViewModel
    private lateinit var adapter: MemoAdapter


    private val selectedIds = mutableSetOf<Long>()
    private var selectionMode: Boolean = false
    private var deleteMenuItem: MenuItem? = null

    companion object {
        const val REQUEST_ADD_MEMO = 1
        const val REQUEST_EDIT_MEMO = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isAccountReady) return
        binding = ActivityMemorandumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.black))


        val factory = MemoViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MemoViewModel::class.java]

        setupRecyclerView()


        setupSearch()


        observeData()


        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, AddEditMemoActivity::class.java)
            startActivityForResult(intent, REQUEST_ADD_MEMO)
        }
    }

    private fun setupRecyclerView() {
        adapter = MemoAdapter(
            onItemClick = { memo ->
                if (selectionMode) {

                    toggleSelection(memo)
                } else {

                    val intent = Intent(this, AddEditMemoActivity::class.java).apply {
                        putExtra("MEMO_ID", memo.id)
                    }
                    startActivityForResult(intent, REQUEST_EDIT_MEMO)
                }
            },
            onCompletedChange = { memo, isCompleted ->
                viewModel.toggleCompleted(memo.id, isCompleted)
            },
            onItemLongClick = { memo ->
                selectionMode = true
                updateToolbarTitle()
                updateMenuVisibility()
                toggleSelection(memo)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MemorandumActivity)
            adapter = this@MemorandumActivity.adapter
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText ?: "")
                return true
            }
        })
        

        binding.searchView.setOnCloseListener {
            binding.searchView.setQuery("", false)
            viewModel.clearSearch()
            true
        }


        binding.searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.apply {
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
    }

    private fun observeData() {
        viewModel.searchResults.observe(this) { memos ->
            adapter.submitList(memos)
            updateEmptyView(memos.isEmpty())
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        deleteMenuItem = menu?.findItem(R.id.action_delete_selected)
        updateMenuVisibility()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {

                if (selectionMode) {
                    clearSelection()
                    true
                } else {
                    val intent = Intent(this, MainPage::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    true
                }
            }
            R.id.action_sort -> {
                if (!selectionMode) {
                    showSortDialog()
                }
                true
            }
            R.id.action_delete_selected -> {
                deleteSelectedMemos()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {

        val options = arrayOf(
            getString(R.string.sort_by_updated),
            getString(R.string.sort_by_created),
            getString(R.string.by_title)
        )
        val currentSort = viewModel.getSortOrder()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_sort)
            .setSingleChoiceItems(options, currentSort) { dialog, which ->
                viewModel.setSortOrder(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun toggleSelection(memo: com.lifeHub.todo.data.Memo) {
        val id = memo.id
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }

        adapter.setSelectedIds(selectedIds)

        selectionMode = selectedIds.isNotEmpty()
        updateToolbarTitle()
        updateMenuVisibility()
    }

    private fun clearSelection() {
        selectedIds.clear()
        selectionMode = false
        adapter.setSelectedIds(emptySet())
        updateToolbarTitle()
        updateMenuVisibility()
        binding.fabAdd.show()
    }


    private fun deleteSelectedMemos() {
        val idsToDelete = selectedIds.toList()
        if (idsToDelete.isEmpty()) return

        idsToDelete.forEach { id ->
            viewModel.deleteById(id)
        }

        Snackbar.make(
            binding.root,
            getString(R.string.memo_deleted),
            Snackbar.LENGTH_SHORT
        ).show()


        clearSelection()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_ADD_MEMO -> {
                    Snackbar.make(binding.root, R.string.memo_saved, Snackbar.LENGTH_SHORT).show()
                }
                REQUEST_EDIT_MEMO -> {
                    val deleted = data?.getBooleanExtra("DELETED", false) ?: false
                    val message = if (deleted) R.string.memo_deleted else R.string.memo_saved
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateToolbarTitle() {
        if (selectionMode) {

            binding.fabAdd.hide()
        } else {
            supportActionBar?.title = getString(R.string.title_memorandum)
            binding.fabAdd.show()
        }
    }

    private fun updateMenuVisibility() {
        deleteMenuItem?.isVisible = selectionMode

    }
}
