package com.example.productprice;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowManager;

import com.example.productprice.data.AppProfileStore;
import com.example.productprice.security.AppLockManager;
import com.example.productprice.ui.MainToolsPopup;
import com.google.android.material.appbar.MaterialToolbar;

public class ProductPriceApplication extends Application
        implements Application.ActivityLifecycleCallbacks {

    private static final int MENU_PROFILE = 91001;

    private AppLockManager appLockManager;
    private int startedActivities = 0;
    private boolean lockLaunchInProgress = false;

    @Override
    public void onCreate() {
        super.onCreate();
        appLockManager = new AppLockManager(this);
        registerActivityLifecycleCallbacks(this);
    }

    public AppLockManager getAppLockManager() {
        return appLockManager;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        applySecureScreen(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        startedActivities++;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        applySecureScreen(activity);

        if (activity instanceof AppLockActivity) {
            lockLaunchInProgress = false;
            return;
        }

        if (activity instanceof MainActivity) {
            configureMainDashboard((MainActivity) activity);
        }

        if (!lockLaunchInProgress && appLockManager.shouldShowLockScreen()) {
            lockLaunchInProgress = true;
            Intent intent = new Intent(activity, AppLockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);

        if (startedActivities == 0 && !activity.isChangingConfigurations()) {
            appLockManager.markAppBackgrounded();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    private void applySecureScreen(Activity activity) {
        if (activity == null) {
            return;
        }

        if (appLockManager.isSecureScreenEnabled()) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void configureMainDashboard(MainActivity activity) {
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        AppProfileStore profileStore = new AppProfileStore(activity);
        String organization = profileStore.getOrganization();
        toolbar.setSubtitle(
                organization.isEmpty()
                        ? "Product Price Pro"
                        : organization
        );

        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationContentDescription("Open tools menu");
        toolbar.setNavigationOnClickListener(
                view -> MainToolsPopup.show(activity, toolbar)
        );

        hideDashboardToolButtons(activity);
        configureProfileAction(activity, toolbar);
    }

    private void hideDashboardToolButtons(MainActivity activity) {
        View productButton = activity.findViewById(R.id.button_manage_products);
        View priceButton = activity.findViewById(R.id.button_update_prices);
        View customerButton = activity.findViewById(R.id.button_manage_customers);

        if (productButton != null) {
            ViewParent parent = productButton.getParent();
            if (parent instanceof View) {
                ((View) parent).setVisibility(View.GONE);
            } else {
                productButton.setVisibility(View.GONE);
            }
        }

        if (priceButton != null) {
            priceButton.setVisibility(View.GONE);
        }

        if (customerButton != null) {
            customerButton.setVisibility(View.GONE);
        }
    }

    private void configureProfileAction(MainActivity activity, MaterialToolbar toolbar) {
        Menu menu = toolbar.getMenu();
        MenuItem profileItem = menu.findItem(MENU_PROFILE);

        if (profileItem == null) {
            profileItem = menu.add(Menu.NONE, MENU_PROFILE, Menu.NONE, "Profile");
            profileItem.setIcon(R.drawable.ic_profile);
            profileItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_PROFILE) {
                activity.startActivity(new Intent(activity, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }
}
