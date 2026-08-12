package com.example.productprice.util;

import com.example.productprice.model.OfficialPriceUpdate;

import java.util.List;

public final class PriceChangeIntelligence {

    private PriceChangeIntelligence() {
    }

    public static Summary summarize(List<OfficialPriceUpdate> updates) {
        Summary summary = new Summary();
        if (updates == null) return summary;

        double percentTotal = 0d;
        int percentCount = 0;

        for (OfficialPriceUpdate update : updates) {
            if (update == null) continue;

            int delta = update.getNewFullPrice() - update.getOldFullPrice();
            if (delta > 0) {
                summary.increasedProducts++;
            } else if (delta < 0) {
                summary.decreasedProducts++;
            } else if (update.isChanged()) {
                summary.tierOnlyChanges++;
            } else {
                summary.unchangedProducts++;
            }

            summary.totalFullPriceDelta += delta;

            if (update.getOldFullPrice() > 0) {
                double percent = (delta * 100d) / update.getOldFullPrice();
                percentTotal += percent;
                percentCount++;

                if (summary.largestIncrease == null
                        || percent > summary.largestIncreasePercent) {
                    summary.largestIncrease = update;
                    summary.largestIncreasePercent = percent;
                }

                if (summary.largestDecrease == null
                        || percent < summary.largestDecreasePercent) {
                    summary.largestDecrease = update;
                    summary.largestDecreasePercent = percent;
                }
            }
        }

        summary.averageFullPricePercent = percentCount == 0
                ? 0d
                : percentTotal / percentCount;

        if (summary.largestIncreasePercent <= 0d) {
            summary.largestIncrease = null;
            summary.largestIncreasePercent = 0d;
        }

        if (summary.largestDecreasePercent >= 0d) {
            summary.largestDecrease = null;
            summary.largestDecreasePercent = 0d;
        }

        return summary;
    }

    public static String directionLabel(OfficialPriceUpdate update) {
        if (update == null || !update.isChanged()) return "No Change";
        int delta = update.getNewFullPrice() - update.getOldFullPrice();
        if (delta > 0) return "Price Increased";
        if (delta < 0) return "Price Decreased";
        return "Discount Tiers Changed";
    }

    public static double fullPricePercent(OfficialPriceUpdate update) {
        if (update == null || update.getOldFullPrice() <= 0) return 0d;
        return ((update.getNewFullPrice() - update.getOldFullPrice()) * 100d)
                / update.getOldFullPrice();
    }

    public static class Summary {
        private int increasedProducts;
        private int decreasedProducts;
        private int tierOnlyChanges;
        private int unchangedProducts;
        private long totalFullPriceDelta;
        private double averageFullPricePercent;
        private OfficialPriceUpdate largestIncrease;
        private OfficialPriceUpdate largestDecrease;
        private double largestIncreasePercent;
        private double largestDecreasePercent;

        public int getIncreasedProducts() {
            return increasedProducts;
        }

        public int getDecreasedProducts() {
            return decreasedProducts;
        }

        public int getTierOnlyChanges() {
            return tierOnlyChanges;
        }

        public int getUnchangedProducts() {
            return unchangedProducts;
        }

        public long getTotalFullPriceDelta() {
            return totalFullPriceDelta;
        }

        public double getAverageFullPricePercent() {
            return averageFullPricePercent;
        }

        public OfficialPriceUpdate getLargestIncrease() {
            return largestIncrease;
        }

        public OfficialPriceUpdate getLargestDecrease() {
            return largestDecrease;
        }

        public double getLargestIncreasePercent() {
            return largestIncreasePercent;
        }

        public double getLargestDecreasePercent() {
            return largestDecreasePercent;
        }
    }
}
