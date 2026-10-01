package com.lifeHub.main.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.lifeHub.R;
import com.lifeHub.login.LoginActivity;
import com.lifeHub.login.LoginManager;
import com.google.android.material.imageview.ShapeableImageView;

public class ProfileFragment extends Fragment {

    private ShapeableImageView imgAvatar;
    private TextView tvUsername;
    private Button btnLogout;

    private final ActivityResultLauncher<String> pickMedia = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if(uri!=null) loadAvatar(uri); }
    );
    private static final java.util.concurrent.Executor AVATAR_WORKER=java.util.concurrent.Executors.newSingleThreadExecutor();
    private int avatarRequest;
    private void loadAvatar(@Nullable Uri source) {
        if(!isAdded() || getView()==null)return;
        String username=LoginManager.getCurrentUser(requireContext());
        if(username.isEmpty())return;
        Context context=requireContext().getApplicationContext();
        View view=getView();int request=++avatarRequest;
        AVATAR_WORKER.execute(()->{
            Uri result=null;boolean failed=false;
            try { result=source==null?com.lifeHub.login.AvatarStore.restore(context,username):com.lifeHub.login.AvatarStore.importAvatar(context,username,source); }
            catch(Exception error){failed=true;}
            final Uri value=result;final boolean error=failed;
            view.post(()->{
                if(!isAdded() || getView()!=view || request!=avatarRequest || !username.equals(LoginManager.getCurrentUser(context)))return;
                if(value!=null)imgAvatar.setImageURI(value);
                if(error)Toast.makeText(requireContext(),R.string.avatar_load_failed,Toast.LENGTH_LONG).show();
                else if(source!=null)Toast.makeText(requireContext(),R.string.avatar_updated,Toast.LENGTH_SHORT).show();
            });
        });
    }
    @Override public void onDestroyView(){avatarRequest++;super.onDestroyView();}

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        imgAvatar = view.findViewById(R.id.img_avatar);
        tvUsername = view.findViewById(R.id.tv_username);

        btnLogout = view.findViewById(R.id.btn_logout);
        Button language = view.findViewById(R.id.btnLanguage);
        String tags = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags();
        String[] names = {getString(R.string.ui_language_system), "English", "简体中文"};
        int choice = tags.startsWith("zh") ? 2 : tags.startsWith("en") ? 1 : 0;
        language.setText(getString(R.string.ui_language) + " · " + names[choice]);
        language.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ui_language).setSingleChoiceItems(names,choice,(dialog,which) -> {
                dialog.dismiss();
                String[] locales={"","en","zh-CN"};
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(locales[which]));
            }).setNegativeButton(R.string.cancel,null).show());


        String username = LoginManager.getCurrentUser(requireContext());
        boolean loggedIn = LoginManager.isLoggedIn(requireContext());


        if (loggedIn && !username.isEmpty()) {
            tvUsername.setText(username);
        } else {
            tvUsername.setText(getString(R.string.profile_username_placeholder));
        }


        if (loggedIn && !username.isEmpty()) loadAvatar(null);


        imgAvatar.setOnClickListener(v -> {
            if (loggedIn) {
                pickMedia.launch("image/*");
            } else {
                Toast.makeText(requireContext(), R.string.avatar_login, Toast.LENGTH_SHORT).show();
            }
        });


        btnLogout.setOnClickListener(v -> {
            if (!loggedIn) {
                Toast.makeText(requireContext(),
                        R.string.profile_not_logged_in,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            LoginManager.logout(requireContext());
            Toast.makeText(requireContext(),
                    R.string.profile_logged_out,
                    Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }


}
