package com.anistream.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Tab "Cari": kolom pencarian, riwayat kata kunci, dan hasil grid.
 */
public class SearchFragment extends Fragment {

    /** Query titipan dari tab lain (mis. chip genre di Beranda). */
    private static String pendingQuery;

    /** Titip query lalu pindah ke tab Cari — dijalankan saat tab tampil. */
    public static void requestQuery(String q) {
        pendingQuery = q == null ? "" : q.trim();
    }

    private AnimeAdapter adapter;
    private EditText input;
    private ProgressBar progress;
    private View emptyBox;
    private TextView empty;
    private LinearLayout recentsBox;
    private LinearLayout recentsList;
    private View btnClear;
    private boolean searched;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup grp,
                             @Nullable Bundle st) {
        View v = inf.inflate(R.layout.fragment_search, grp, false);

        input = v.findViewById(R.id.input);
        btnClear = v.findViewById(R.id.btnClear);
        MaterialButton btn = v.findViewById(R.id.btnSearch);
        progress = v.findViewById(R.id.progress);
        emptyBox = v.findViewById(R.id.emptyBox);
        empty = v.findViewById(R.id.empty);
        ImageView emptyIcon = v.findViewById(R.id.emptyIcon);
        recentsBox = v.findViewById(R.id.recentsBox);
        recentsList = v.findViewById(R.id.recentsList);
        RecyclerView rv = v.findViewById(R.id.recycler);

        // Hapus seluruh riwayat pencarian (kunci disimpan di Prefs.recents).
        MaterialButton btnClearRecents = v.findViewById(R.id.uiClearRecents);
        btnClearRecents.setOnClickListener(x -> {
            if (getContext() == null) return;
            Prefs.clearRecents(requireContext());
            showRecents();
        });

        adapter = new AnimeAdapter(this::open);
        rv.setLayoutManager(grid());
        rv.setAdapter(adapter);

        emptyIcon.setImageResource(R.drawable.ic_search);
        empty.setText(R.string.empty_search);

        btn.setOnClickListener(x -> doSearch());
        btnClear.setOnClickListener(x -> {
            input.setText("");
            btnClear.setVisibility(View.GONE);
            showRecents();
        });
        input.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s) {
                btnClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        showRecents();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Jalankan titipan query dari Beranda (chip genre) sekali saja.
        if (pendingQuery != null && !pendingQuery.isEmpty()
                && input != null && isAdded()) {
            input.setText(pendingQuery);
            pendingQuery = "";
            doSearch();
        }
    }

    private GridLayoutManager grid() {
        int orientation = getResources().getConfiguration().orientation;
        int span = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 5 : 3;
        return new GridLayoutManager(requireContext(), span);
    }

    // -------------------------------------------------------- pencarian

    private void doSearch() {
        final String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        if (getContext() == null) return;

        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

        progress.setVisibility(View.VISIBLE);
        emptyBox.setVisibility(View.GONE);

        Async.go(() -> Oploverz.search(q), new Async.Done<List<AnimeItem>>() {
            @Override public void ok(List<AnimeItem> items) {
                progress.setVisibility(View.GONE);
                adapter.submit(items);
                searched = true;
                if (!items.isEmpty()) Prefs.addRecent(requireContext(), q);
                showEmpty(items.isEmpty() ? R.string.empty_search2 : 0);
                showRecents();
            }

            @Override public void err(Throwable t) {
                progress.setVisibility(View.GONE);
                showEmpty(R.string.err_net);
            }
        });
    }

    private void showEmpty(int msg) {
        if (msg == 0) {
            emptyBox.setVisibility(View.GONE);
            return;
        }
        emptyBox.setVisibility(View.VISIBLE);
        empty.setText(msg);
    }

    /** Tampilkan daftar kata kunci terakhir hanya sebelum ada hasil. */
    private void showRecents() {
        if (recentsBox == null || recentsList == null || getContext() == null) return;
        List<String> recents = Prefs.recents(requireContext());

        boolean show = !recents.isEmpty() && !searched;
        recentsBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        recentsList.removeAllViews();
        for (final String q : recents) {
            MaterialButton chip = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(q);
            chip.setAllCaps(false);
            chip.setTextSize(12);
            chip.setMinHeight(0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setCornerRadius(40);
            // Outline pil mengikuti aksen tema aktif (lihat res/color/ui_chip_stroke).
            chip.setStrokeColor(androidx.core.content.ContextCompat.getColorStateList(
                    requireContext(), R.color.ui_chip_stroke));
            chip.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(16);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(x -> {
                input.setText(q);
                input.setSelection(q.length());
                doSearch();
            });
            recentsList.addView(chip);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void open(AnimeItem item) {
        if (getContext() == null) return;
        Intent i = new Intent(requireContext(), SeriesActivity.class);
        i.putExtra("url", item.url);
        i.putExtra("title", item.title);
        i.putExtra("thumb", item.thumb);
        startActivity(i);
    }
}
