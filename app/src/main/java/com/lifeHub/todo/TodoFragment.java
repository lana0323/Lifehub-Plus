package com.lifeHub.todo;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.navigation.fragment.NavHostFragment;
import android.graphics.Color;
import android.widget.EditText;

import com.lifeHub.todo.ui.AddEditMemoActivity;
import com.lifeHub.todo.ui.MemoAdapter;
import com.google.android.material.snackbar.Snackbar;
import com.lifeHub.R;
import com.lifeHub.databinding.ActivityMemorandumBinding;
import com.lifeHub.todo.viewmodel.MemoViewModel;
import com.lifeHub.todo.viewmodel.MemoViewModelFactory;

import kotlin.Unit;

public class TodoFragment extends Fragment {

    private ActivityMemorandumBinding binding;
    private MemoViewModel viewModel;
    private MemoAdapter adapter;

    private final java.util.Set<Long> selectedIds = new java.util.HashSet<>();
    private boolean selectionMode = false;
    private MenuItem deleteMenuItem;

    private static final int REQUEST_ADD_MEMO = 1;
    private static final int REQUEST_EDIT_MEMO = 2;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.activity_memorandum, container, false);
        binding = ActivityMemorandumBinding.bind(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(binding.toolbar);
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        if (binding.toolbar.getNavigationIcon() != null) {
            binding.toolbar.getNavigationIcon().setTint(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brand_teal_dark)
            );
        }
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .popBackStack(R.id.navigation_home, false)
        );

        MemoViewModelFactory factory = new MemoViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), factory).get(MemoViewModel.class);

        setupRecyclerView();
        setupSearch();
        observeData();

        binding.fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddEditMemoActivity.class);
            startActivityForResult(intent, REQUEST_ADD_MEMO);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }



    private void setupRecyclerView() {
        adapter = new MemoAdapter(


                memo -> {

                    if (selectionMode) {
                        toggleSelection(memo);
                    } else {
                        Intent intent = new Intent(requireContext(), AddEditMemoActivity.class);
                        intent.putExtra("MEMO_ID", memo.getId());
                        startActivityForResult(intent, REQUEST_EDIT_MEMO);
                    }
                    return kotlin.Unit.INSTANCE;
                },


                (memo, isCompleted) -> {

                    return kotlin.Unit.INSTANCE;
                },


                memo -> {
                    selectionMode = true;
                    updateToolbarTitle();
                    updateMenuVisibility();
                    toggleSelection(memo);
                    return kotlin.Unit.INSTANCE;
                }
        );

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
    }



    private void setupSearch() {
        binding.searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                viewModel.search(newText == null ? "" : newText);
                return true;
            }
        });
        

        binding.searchView.setOnCloseListener(() -> {
            binding.searchView.setQuery("", false);
            viewModel.clearSearch();
            return true;
        });


        android.widget.EditText searchEditText = binding.searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchEditText != null) {
            searchEditText.setTextColor(android.graphics.Color.BLACK);
            searchEditText.setHintTextColor(android.graphics.Color.GRAY);
        }
    }



    private void observeData() {
        viewModel.getSearchResults().observe(
                getViewLifecycleOwner(),
                memos -> {
                    adapter.submitList(memos);
                    updateEmptyView(memos.isEmpty());
                }
        );
    }

    private void updateEmptyView(boolean isEmpty) {
        binding.emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }



    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        deleteMenuItem = menu.findItem(R.id.action_delete_selected);
        updateMenuVisibility();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sort) {
            if (!selectionMode) {
                showSortDialog();
            }
            return true;
        } else if (item.getItemId() == R.id.action_delete_selected) {
            deleteSelectedMemos();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {

        String[] options = {
            getString(R.string.sort_by_updated),
            getString(R.string.sort_by_created),
            getString(R.string.by_title)
        };
        int currentSort = viewModel.getSortOrder();

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.menu_sort)
                .setSingleChoiceItems(options, currentSort, (dialog, which) -> {
                    viewModel.setSortOrder(which);
                    dialog.dismiss();
                })
                .show();
    }



    private void toggleSelection(com.lifeHub.todo.data.Memo memo) {
        long id = memo.getId();
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }

        adapter.setSelectedIds(selectedIds);

        selectionMode = !selectedIds.isEmpty();
        updateToolbarTitle();
        updateMenuVisibility();
    }

    private void deleteSelectedMemos() {
        if (selectedIds.isEmpty()) return;

        java.util.List<Long> idsToDelete = new java.util.ArrayList<>(selectedIds);
        for (Long id : idsToDelete) {
            viewModel.deleteById(id, null);
        }

        if (binding != null) {
            Snackbar.make(
                    binding.getRoot(),
                    getString(R.string.memo_deleted),
                    Snackbar.LENGTH_SHORT
            ).show();
        }

        clearSelection();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == AppCompatActivity.RESULT_OK) {
            if (requestCode == REQUEST_ADD_MEMO) {
                Snackbar.make(binding.getRoot(), R.string.memo_saved, Snackbar.LENGTH_SHORT).show();
            } else if (requestCode == REQUEST_EDIT_MEMO) {
                boolean deleted = data != null && data.getBooleanExtra("DELETED", false);
                Snackbar.make(binding.getRoot(), deleted ? R.string.memo_deleted : R.string.memo_saved, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void clearSelection() {
        selectedIds.clear();
        adapter.setSelectedIds(java.util.Collections.emptySet());
        selectionMode = false;
        updateToolbarTitle();
        updateMenuVisibility();
        if (binding != null) {
            binding.fabAdd.show();
        }
    }

    private void updateToolbarTitle() {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (selectionMode) {
            if (activity.getSupportActionBar() != null) {

                activity.getSupportActionBar().setTitle(getString(R.string.title_memorandum));
            }
            if (binding != null) binding.fabAdd.hide();
        } else {
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(getString(R.string.title_memorandum));
            }
            if (binding != null) binding.fabAdd.show();
        }
    }

    private void updateMenuVisibility() {
        if (deleteMenuItem != null) {
            deleteMenuItem.setVisible(selectionMode);
        }
    }
}

