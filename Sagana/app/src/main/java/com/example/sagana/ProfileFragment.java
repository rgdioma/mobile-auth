package com.example.sagana;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Adjust padding for status bar
        final int basePadding = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), basePadding + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        String name = requireActivity().getIntent().getStringExtra(HomeActivity.EXTRA_DISPLAY_NAME);
        if (name == null || name.isEmpty()) {
            name = getString(R.string.default_profile_name);
        }
        ((TextView) view.findViewById(R.id.textName)).setText(name);

        // The SDG card is static; only its link leaves the app.
        view.findViewById(R.id.textSdgLink).setOnClickListener(v -> openSdgPage());

        view.findViewById(R.id.rowEditProfile).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.toast_coming_soon, Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.buttonLogout).setOnClickListener(v -> logout());
    }

    private void openSdgPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sdg_url)));
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(), R.string.toast_coming_soon, Toast.LENGTH_SHORT).show();
        }
    }

    private void logout() {
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
