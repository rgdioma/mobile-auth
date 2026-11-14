package com.example.sagana;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.animation.Easing;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Charts and reports from the database (MPAndroidChart):
 *   stacked bar   handovers per day, picked up vs expired
 *   donut         open listings by category
 *   horizontal    busiest pickup desks
 * "Export report (PDF)" writes Download/sagana_report_yyyyMMdd_HHmm.pdf.
 */
public class ReportsFragment extends Fragment {

    private static final String ARG_BACK = "back";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ReportData data;
    private int ink, muted, grid;

    public ReportsFragment() {
        super(R.layout.fragment_reports);
    }

    /** Members open it as its own screen with a back arrow. */
    public static ReportsFragment withBack() {
        ReportsFragment f = new ReportsFragment();
        Bundle b = new Bundle();
        b.putBoolean(ARG_BACK, true);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean back = getArguments() != null && getArguments().getBoolean(ARG_BACK);
        if (back) {
            view.findViewById(R.id.buttonBack).setVisibility(View.VISIBLE);
            view.findViewById(R.id.buttonBack).setOnClickListener(v -> requireActivity().finish());
            ((TextView) view.findViewById(R.id.textReportTitle)).setText(R.string.title_impact);
        } else {
            Ui.padForStatusBar(view);
        }
        ink = ContextCompat.getColor(requireContext(), R.color.adobo);
        muted = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        grid = ContextCompat.getColor(requireContext(), R.color.input_border);

        data = ReportData.load(DatabaseHelper.get(requireContext()));
        showKpis(view);
        showPerDay(view.findViewById(R.id.barChart));
        showCategories(view.findViewById(R.id.pieChart));
        showDesks(view.findViewById(R.id.deskChart));
        Ui.stagger(view.findViewById(R.id.reportBody));

        view.findViewById(R.id.buttonExport).setOnClickListener(v -> exportPdf());
    }

    // ------------------------------------------------------------------ tiles
    private void showKpis(View v) {
        ((TextView) v.findViewById(R.id.kpiKg)).setText(String.format(Locale.US, "%.1f kg", data.kg));
        ((TextView) v.findViewById(R.id.kpiHandovers)).setText(String.valueOf(data.totalPicked));
        ((TextView) v.findViewById(R.id.kpiRate)).setText(data.rescueRate() + "%");
        ((TextView) v.findViewById(R.id.kpiExpired)).setText(getString(R.string.n_expired, data.totalExpired));
        ((TextView) v.findViewById(R.id.kpiActive)).setText(String.valueOf(data.active));
    }

    // ------------------------------------------------------------------ charts
    private void showPerDay(BarChart chart) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new SimpleDateFormat("EEE", Locale.US);
        for (int i = 0; i < 7; i++) {
            entries.add(new BarEntry(i, new float[]{data.picked[i], data.expired[i]}));
            String label;
            try { label = i == 6 ? "Today" : out.format(in.parse(data.days.get(i))); }
            catch (Exception e) { label = data.days.get(i).substring(5); }
            labels.add(label);
        }
        BarDataSet set = new BarDataSet(entries, "");
        set.setColors(ContextCompat.getColor(requireContext(), R.color.chili), Color.parseColor("#F2C9A0"));
        set.setStackLabels(new String[]{getString(R.string.legend_picked), getString(R.string.legend_expired)});
        set.setDrawValues(false);
        BarData bd = new BarData(set);
        bd.setBarWidth(0.62f);
        chart.setData(bd);
        styleAxes(chart);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setLabelCount(7);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setGranularity(1f);
        chart.animateY(900, Easing.EaseOutCubic);
        chart.invalidate();
    }

    private void showCategories(PieChart chart) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < ReportData.CATEGORIES.length; i++) {
            Integer n = data.byCategory.get(ReportData.CATEGORIES[i]);
            if (n == null || n == 0) continue;
            entries.add(new PieEntry(n, ReportData.CATEGORIES[i]));
            colors.add(ReportData.CATEGORY_COLORS[i]);
            total += n;
        }
        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        set.setSliceSpace(2f);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(12f);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        chart.setData(new PieData(set));
        chart.setHoleRadius(52f);
        chart.setTransparentCircleRadius(56f);
        chart.setCenterText(total + "\nactive");
        chart.setCenterTextSize(16f);
        chart.setCenterTextColor(ink);
        chart.setDrawEntryLabels(false);
        chart.getDescription().setEnabled(false);
        chart.setNoDataText(getString(R.string.chart_no_data));
        Legend l = chart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.CENTER);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        l.setOrientation(Legend.LegendOrientation.VERTICAL);
        l.setTextColor(ink);
        l.setTextSize(12f);
        chart.animateY(1000, Easing.EaseInOutQuad);
        chart.invalidate();
    }

    private void showDesks(HorizontalBarChart chart) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(data.byDesk.entrySet());
        // the horizontal chart draws index 0 at the bottom, so add busiest last
        for (int i = rows.size() - 1, x = 0; i >= 0; i--, x++) {
            entries.add(new BarEntry(x, rows.get(i).getValue()));
            labels.add("Brgy. " + Geo.barangayOf(rows.get(i).getKey()));
        }
        BarDataSet set = new BarDataSet(entries, getString(R.string.chart_desks));
        set.setColor(ContextCompat.getColor(requireContext(), R.color.chili));
        set.setValueTextColor(ink);
        set.setValueTextSize(11f);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        BarData bd = new BarData(set);
        bd.setBarWidth(0.6f);
        chart.setData(bd);
        styleAxes(chart);
        chart.getLegend().setEnabled(false);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setLabelCount(labels.size());
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setGranularity(1f);
        chart.animateY(900, Easing.EaseOutCubic);
        chart.invalidate();
    }

    private void styleAxes(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setScaleEnabled(false);
        // no tap highlight, so the exported PDF never shows a darkened bar
        chart.setHighlightPerTapEnabled(false);
        chart.setHighlightPerDragEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setTextColor(muted);
        chart.getAxisLeft().setGridColor(grid);
        chart.getAxisLeft().setDrawAxisLine(false);
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        x.setTextColor(muted);
        chart.getLegend().setTextColor(ink);
        chart.setNoDataText(getString(R.string.chart_no_data));
    }

    // ------------------------------------------------------------------ PDF export
    private void exportPdf() {
        View v = getView();
        if (v == null) return;
        v.findViewById(R.id.buttonExport).setEnabled(false);
        // snapshot the charts as they look on screen
        Bitmap bar = ((BarChart) v.findViewById(R.id.barChart)).getChartBitmap();
        Bitmap pie = ((PieChart) v.findViewById(R.id.pieChart)).getChartBitmap();
        Bitmap desk = ((HorizontalBarChart) v.findViewById(R.id.deskChart)).getChartBitmap();
        DatabaseHelper.User me = Session.user(requireContext());
        String by = me == null ? "" : me.name + (me.isStaff() ? " (" + me.desk() + ")" : "");
        android.content.Context app = requireContext().getApplicationContext();

        IO.execute(() -> {
            String where = null, error = null;
            try {
                byte[] pdf = ReportPdf.build(app, data, bar, pie, desk, by);
                String name = "sagana_report_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".pdf";
                where = BackupManager.saveToDownloads(app, name, "application/pdf", pdf);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String savedTo = where, failed = error;
            v.post(() -> {
                if (!isAdded()) return;
                v.findViewById(R.id.buttonExport).setEnabled(true);
                if (failed != null) {
                    Toast.makeText(requireContext(), getString(R.string.toast_export_failed, failed), Toast.LENGTH_LONG).show();
                    return;
                }
                AlertDialog.Builder dlg = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.report_saved_title)
                        .setMessage(getString(R.string.report_saved_body, savedTo))
                        .setNegativeButton(R.string.action_done, null);
                if (BackupManager.lastSavedUri != null) dlg.setPositiveButton(R.string.action_open, (d, w) -> openPdf());
                dlg.show();
            });
        });
    }

    private void openPdf() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(BackupManager.lastSavedUri, "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.toast_no_pdf_app, Toast.LENGTH_LONG).show();
        }
    }
}
