package com.example.productprice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.productprice.data.AppProfileStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileActivity extends AppCompatActivity {

    private AppProfileStore profileStore;

    private ImageView profilePhoto;
    private TextView initialsText;
    private TextView displayNameText;
    private TextView organizationText;

    private TextInputEditText nameInput;
    private TextInputEditText organizationInput;
    private TextInputEditText phoneInput;
    private TextInputEditText emailInput;

    private final ActivityResultLauncher<String[]> photoPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::handleSelectedPhoto
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        profileStore = new AppProfileStore(this);
        bindViews();
        loadProfile();
        setupActions();
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_profile);
        toolbar.setNavigationOnClickListener(view -> finish());

        profilePhoto = findViewById(R.id.image_profile_photo);
        initialsText = findViewById(R.id.text_profile_initials);
        displayNameText = findViewById(R.id.text_profile_display_name);
        organizationText = findViewById(R.id.text_profile_organization);

        nameInput = findViewById(R.id.input_profile_name);
        organizationInput = findViewById(R.id.input_profile_organization);
        phoneInput = findViewById(R.id.input_profile_phone);
        emailInput = findViewById(R.id.input_profile_email);
    }

    private void setupActions() {
        findViewById(R.id.button_choose_profile_photo).setOnClickListener(
                view -> photoPicker.launch(new String[]{"image/*"})
        );

        findViewById(R.id.button_remove_profile_photo).setOnClickListener(
                view -> {
                    profileStore.setPhotoUri("");
                    renderPhoto();
                    Toast.makeText(this, "Profile photo removed", Toast.LENGTH_SHORT).show();
                }
        );

        findViewById(R.id.button_save_profile).setOnClickListener(
                view -> saveProfile()
        );

        findViewById(R.id.button_open_security).setOnClickListener(
                view -> startActivity(new Intent(this, AppLockSettingsActivity.class))
        );
    }

    private void loadProfile() {
        nameInput.setText(profileStore.getName());
        organizationInput.setText(profileStore.getOrganization());
        phoneInput.setText(profileStore.getPhone());
        emailInput.setText(profileStore.getEmail());
        renderHeader();
        renderPhoto();
    }

    private void saveProfile() {
        profileStore.save(
                text(nameInput),
                text(phoneInput),
                text(emailInput),
                text(organizationInput)
        );

        renderHeader();
        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
    }

    private void handleSelectedPhoto(Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }

        profileStore.setPhotoUri(uri.toString());
        renderPhoto();
    }

    private void renderHeader() {
        displayNameText.setText(profileStore.getDisplayName());

        String organization = profileStore.getOrganization();
        organizationText.setText(
                organization.isEmpty() ? "Product Price Pro" : organization
        );

        initialsText.setText(profileStore.getInitials());
    }

    private void renderPhoto() {
        String photoUri = profileStore.getPhotoUri();

        if (photoUri.isEmpty()) {
            profilePhoto.setImageDrawable(null);
            profilePhoto.setVisibility(View.GONE);
            initialsText.setVisibility(View.VISIBLE);
            return;
        }

        try {
            profilePhoto.setImageURI(Uri.parse(photoUri));
            profilePhoto.setVisibility(View.VISIBLE);
            initialsText.setVisibility(View.GONE);
        } catch (Exception exception) {
            profileStore.setPhotoUri("");
            profilePhoto.setVisibility(View.GONE);
            initialsText.setVisibility(View.VISIBLE);
        }
    }

    private String text(TextInputEditText input) {
        return input.getText() == null
                ? ""
                : input.getText().toString().trim();
    }
}
