package com.lifeHub.main.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.lifeHub.R;
import com.lifeHub.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavController navController = Navigation.findNavController(requireView());

        binding.tvUserName.setText(R.string.hello);

        binding.cardTodo.setOnClickListener(v ->
                navController.navigate(R.id.nav_todo_fragment));

        binding.cardSchedule.setOnClickListener(v ->
                navController.navigate(R.id.nav_schedule_fragment));

        binding.cardAccount.setOnClickListener(v ->
                navController.navigate(R.id.nav_account_fragment));

        binding.cardHealth.setOnClickListener(v ->
                navController.navigate(R.id.nav_health_fragment));

        binding.btnAiChat.setOnClickListener(v -> {

            navController.navigate(R.id.navigation_ai);
        });

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
