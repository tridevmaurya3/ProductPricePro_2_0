package com.example.productprice.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.productprice.model.CompanyProductMapping;
import com.example.productprice.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores manual app-product -> official-company-product mappings locally.
 * SharedPreferences is sufficient here because the mapping set is tiny and
 * should survive normal app restarts and Android backup/restore.
 */
public class CompanyProductMappingStore {

    private static final String PREFS = "company_product_mapping_memory";
    private static final String KEY_INDEX = "mapping_index";

    private static final String SUFFIX_APP_NAME = ".app_name";
    private static final String SUFFIX_APP_CATEGORY = ".app_category";
    private static final String SUFFIX_GROUP = ".company_group";
    private static final String SUFFIX_COMPANY_NAME = ".company_name";
    private static final String SUFFIX_STOCK = ".stock";
    private static final String SUFFIX_SAVED_AT = ".saved_at";

    private final SharedPreferences preferences;

    public CompanyProductMappingStore(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }

        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(
            Product product,
            String companyGroupKey,
            String companyProductName,
            String stockNo
    ) {
        if (product == null) {
            return;
        }

        String appKey = CompanyProductMapping.keyFor(product);
        if (appKey.isEmpty() || safe(companyGroupKey).isEmpty()) {
            return;
        }

        Set<String> index = new HashSet<>(
                preferences.getStringSet(KEY_INDEX, Collections.emptySet())
        );
        index.add(appKey);

        preferences.edit()
                .putStringSet(KEY_INDEX, index)
                .putString(appKey + SUFFIX_APP_NAME, safe(product.getName()))
                .putString(appKey + SUFFIX_APP_CATEGORY, safe(product.getCategory()))
                .putString(appKey + SUFFIX_GROUP, safe(companyGroupKey))
                .putString(appKey + SUFFIX_COMPANY_NAME, safe(companyProductName))
                .putString(appKey + SUFFIX_STOCK, safe(stockNo))
                .putLong(appKey + SUFFIX_SAVED_AT, System.currentTimeMillis())
                .apply();
    }

    public CompanyProductMapping get(Product product) {
        if (product == null) {
            return null;
        }
        return getByKey(CompanyProductMapping.keyFor(product));
    }

    public CompanyProductMapping getByKey(String appProductKey) {
        String key = safe(appProductKey);
        if (key.isEmpty()) {
            return null;
        }

        String groupKey = preferences.getString(key + SUFFIX_GROUP, "");
        if (groupKey == null || groupKey.trim().isEmpty()) {
            return null;
        }

        return new CompanyProductMapping(
                key,
                preferences.getString(key + SUFFIX_APP_NAME, ""),
                preferences.getString(key + SUFFIX_APP_CATEGORY, ""),
                groupKey,
                preferences.getString(key + SUFFIX_COMPANY_NAME, ""),
                preferences.getString(key + SUFFIX_STOCK, ""),
                preferences.getLong(key + SUFFIX_SAVED_AT, 0L)
        );
    }

    public Map<String, CompanyProductMapping> getAllMappings() {
        Map<String, CompanyProductMapping> result = new HashMap<>();

        Set<String> index = preferences.getStringSet(
                KEY_INDEX,
                Collections.emptySet()
        );

        if (index == null) {
            return result;
        }

        for (String appKey : index) {
            CompanyProductMapping mapping = getByKey(appKey);
            if (mapping != null) {
                result.put(appKey, mapping);
            }
        }

        return result;
    }

    public List<CompanyProductMapping> getAllMappingsSorted() {
        List<CompanyProductMapping> result = new ArrayList<>(
                getAllMappings().values()
        );

        Collections.sort(
                result,
                (left, right) -> left.getAppProductName()
                        .compareToIgnoreCase(right.getAppProductName())
        );

        return result;
    }

    public int getCount() {
        return getAllMappings().size();
    }

    public void remove(Product product) {
        if (product == null) {
            return;
        }
        removeByKey(CompanyProductMapping.keyFor(product));
    }

    public void removeByKey(String appProductKey) {
        String key = safe(appProductKey);
        if (key.isEmpty()) {
            return;
        }

        Set<String> index = new HashSet<>(
                preferences.getStringSet(KEY_INDEX, Collections.emptySet())
        );
        index.remove(key);

        preferences.edit()
                .putStringSet(KEY_INDEX, index)
                .remove(key + SUFFIX_APP_NAME)
                .remove(key + SUFFIX_APP_CATEGORY)
                .remove(key + SUFFIX_GROUP)
                .remove(key + SUFFIX_COMPANY_NAME)
                .remove(key + SUFFIX_STOCK)
                .remove(key + SUFFIX_SAVED_AT)
                .apply();
    }

    public void clearAll() {
        preferences.edit().clear().apply();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
