/* Copyright (C) 2014  olie.xdev <olie.xdev@googlemail.com>
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>
*/
package com.health.openscale.gui.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.health.openscale.BuildConfig;
import com.health.openscale.R;
import com.health.openscale.core.OpenScale;
import com.health.openscale.core.datatypes.ScaleMeasurement;
import com.health.openscale.core.datatypes.ScaleUser;
import com.health.openscale.gui.activities.DataEntryActivity;
import com.health.openscale.gui.utils.PermissionHelper;
import com.health.openscale.gui.views.MeasurementView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.app.Activity.RESULT_OK;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;

public class TableFragment extends Fragment implements FragmentUpdateListener {
    private View tableView;
    private ListView tableDataView;
    private LinearLayout tableHeaderView;
    private SharedPreferences prefs;
    private LinearLayout subpageView;

    private ImageView optionMenu;
    private PopupMenu popup;

    private List<MeasurementView> measurementViews;

    private int selectedSubpageNr;
    private static final String SELECTED_SUBPAGE_NR_KEY = "selectedSubpageNr";

    private static final int IMPORT_DATA_REQUEST = 100;

    public TableFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        tableView = inflater.inflate(R.layout.fragment_table, container, false);

        subpageView = (LinearLayout) tableView.findViewById(R.id.subpageView);

        tableDataView = (ListView) tableView.findViewById(R.id.tableDataView);
        tableHeaderView = (LinearLayout) tableView.findViewById(R.id.tableHeaderView);

        tableDataView.setAdapter(new ListViewAdapter());
        tableDataView.setOnItemClickListener(new onClickListenerRow());

        optionMenu = (ImageView) tableView.findViewById(R.id.optionMenu);

        prefs = PreferenceManager.getDefaultSharedPreferences(tableView.getContext());

        if (savedInstanceState == null) {
            selectedSubpageNr = 0;
        }
        else {
            selectedSubpageNr = savedInstanceState.getInt(SELECTED_SUBPAGE_NR_KEY);
        }

        measurementViews = MeasurementView.getMeasurementList(getContext());

        for (MeasurementView measurement : measurementViews) {
            measurement.setUpdateViews(false);
        }

        optionMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popup.show();
            }
        });

        popup = new PopupMenu(getContext(), optionMenu);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.importData:
                        importTable();
                        return true;
                    case R.id.exportData:
                        if (PermissionHelper.requestWritePermission(getActivity())) {
                            exportTable();
                        }
                        return true;
                    case R.id.shareData:
                        shareTable();
                        return true;
                    default:
                        return false;
                }
            }
        });
        popup.getMenuInflater().inflate(R.menu.table_menu, popup.getMenu());

        OpenScale.getInstance(getContext()).registerFragment(this);

        return tableView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_SUBPAGE_NR_KEY, selectedSubpageNr);
    }

    @Override
    public void updateOnView(List<ScaleMeasurement> scaleMeasurementList)
    {
        final int maxSize = 25;

        final int subpageCount = (int)Math.ceil(scaleMeasurementList.size() / (double)maxSize);
        if (selectedSubpageNr >= subpageCount) {
            selectedSubpageNr = Math.max(0, subpageCount - 1);
        }

        subpageView.removeAllViews();

        Button moveSubpageLeft = new Button(tableView.getContext());
        moveSubpageLeft.setText("<");
        moveSubpageLeft.setPadding(0,0,0,0);
        moveSubpageLeft.setTextColor(Color.WHITE);
        moveSubpageLeft.setBackground(ContextCompat.getDrawable(tableView.getContext(), R.drawable.flat_selector));
        moveSubpageLeft.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        moveSubpageLeft.getLayoutParams().height = pxImageDp(20);
        moveSubpageLeft.getLayoutParams().width = pxImageDp(50);
        moveSubpageLeft.setOnClickListener(new onClickListenerMoveSubpageLeft());
        moveSubpageLeft.setEnabled(selectedSubpageNr > 0);
        subpageView.addView(moveSubpageLeft);

        for (int i = 0; i < subpageCount; i++) {
            TextView subpageNrView = new TextView(tableView.getContext());
            subpageNrView.setOnClickListener(new onClickListenerSubpageSelect());
            subpageNrView.setText(Integer.toString(i+1));
            subpageNrView.setTextColor(Color.GRAY);
            subpageNrView.setPadding(10, 10, 20, 10);

            subpageView.addView(subpageNrView);
        }

        if (subpageView.getChildCount() > 1) {
            TextView selectedSubpageNrView = (TextView) subpageView.getChildAt(selectedSubpageNr + 1);
            if (selectedSubpageNrView != null) {
                selectedSubpageNrView.setTypeface(null, Typeface.BOLD);
                selectedSubpageNrView.setTextColor(Color.WHITE);
            }
        }

        Button moveSubpageRight = new Button(tableView.getContext());
        moveSubpageRight.setText(">");
        moveSubpageRight.setPadding(0,0,0,0);
        moveSubpageRight.setTextColor(Color.WHITE);
        moveSubpageRight.setBackground(ContextCompat.getDrawable(tableView.getContext(), R.drawable.flat_selector));
        moveSubpageRight.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        moveSubpageRight.getLayoutParams().height = pxImageDp(20);
        moveSubpageRight.getLayoutParams().width = pxImageDp(50);
        moveSubpageRight.setOnClickListener(new onClickListenerMoveSubpageRight());
        moveSubpageRight.setEnabled(selectedSubpageNr + 1 < subpageCount);
        subpageView.addView(moveSubpageRight);

        tableHeaderView.removeAllViews();

        ArrayList<MeasurementView> visibleMeasurements = new ArrayList<>();
        for (MeasurementView measurement : measurementViews) {
            measurement.updatePreferences(prefs);

            if (measurement.isVisible()) {
                ImageView headerIcon = new ImageView(tableView.getContext());
                headerIcon.setImageDrawable(measurement.getIcon());
                headerIcon.setLayoutParams(new TableRow.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.MATCH_PARENT, 1));
                headerIcon.getLayoutParams().width = 0;
                headerIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                headerIcon.getLayoutParams().height = pxImageDp(20);

                tableHeaderView.addView(headerIcon);

                visibleMeasurements.add(measurement);
            }
        }

        ListViewAdapter adapter = (ListViewAdapter) tableDataView.getAdapter();

        final int startOffset = maxSize * selectedSubpageNr;
        final int endOffset = Math.min(startOffset + maxSize + 1, scaleMeasurementList.size());
        adapter.setMeasurements(visibleMeasurements, scaleMeasurementList.subList(startOffset, endOffset), maxSize);
    }

    private int pxImageDp(float dp) {
        return (int)(dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class onClickListenerRow implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Intent intent = new Intent(tableView.getContext(), DataEntryActivity.class);
            intent.putExtra(DataEntryActivity.EXTRA_ID, (int)id);
            startActivity(intent);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == IMPORT_DATA_REQUEST && resultCode == RESULT_OK && data != null) {
            OpenScale.getInstance(getContext()).importData(data.getData());
        }
    }

    private void importTable() {
        int selectedUserId = OpenScale.getInstance(getContext()).getSelectedScaleUserId();

        if (selectedUserId == -1) {
            AlertDialog.Builder infoDialog = new AlertDialog.Builder(getContext());

            infoDialog.setMessage(getResources().getString(R.string.info_no_selected_user));

            infoDialog.setPositiveButton(getResources().getString(R.string.label_ok), null);

            infoDialog.show();
        }
        else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/*");

            startActivityForResult(
                    Intent.createChooser(intent, getResources().getString(R.string.label_import)),
                    IMPORT_DATA_REQUEST);
        }
    }

    private void exportTable() {
        AlertDialog.Builder filenameDialog = new AlertDialog.Builder(getActivity());

        filenameDialog.setTitle(getResources().getString(R.string.info_set_filename) + " " + Environment.getExternalStorageDirectory().getPath());

        final ScaleUser selectedScaleUser = OpenScale.getInstance(getContext()).getSelectedScaleUser();
        String exportFilename = prefs.getString("exportFilename" + selectedScaleUser.getId(), "openScale_data_" + selectedScaleUser.getUserName() + ".csv");

        final EditText txtFilename = new EditText(tableView.getContext());
        txtFilename.setText(exportFilename);

        filenameDialog.setView(txtFilename);

        filenameDialog.setPositiveButton(getResources().getString(R.string.label_export), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                String fullPath = Environment.getExternalStorageDirectory().getPath() + "/" + txtFilename.getText().toString();

                if (OpenScale.getInstance(getContext()).exportData(fullPath)) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(tableView.getContext());
                    prefs.edit().putString("exportFilename" + selectedScaleUser.getId(), txtFilename.getText().toString()).commit();
                    Toast.makeText(getContext(), getResources().getString(R.string.info_data_exported) + " " + fullPath, Toast.LENGTH_SHORT).show();
                }
            }
        });

        filenameDialog.setNegativeButton(getResources().getString(R.string.label_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        filenameDialog.show();
    }

    private void shareTable() {
        final ScaleUser selectedScaleUser = OpenScale.getInstance(getContext()).getSelectedScaleUser();

        File shareFile = new File(getContext().getCacheDir(),
                String.format("openScale %s.csv", selectedScaleUser.getUserName()));
        if (!OpenScale.getInstance(getContext()).exportData(shareFile.getPath())) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("text/csv");

        final Uri uri = FileProvider.getUriForFile(
                getContext(), BuildConfig.APPLICATION_ID + ".fileprovider", shareFile);
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        intent.putExtra(Intent.EXTRA_SUBJECT,
                getResources().getString(R.string.label_share_subject, selectedScaleUser.getUserName()));

        startActivity(Intent.createChooser(intent, getResources().getString(R.string.label_share)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PermissionHelper.PERMISSIONS_REQUEST_ACCESS_READ_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    importTable();
                } else {
                    Toast.makeText(getContext(), getResources().getString(R.string.permission_not_granted), Toast.LENGTH_SHORT).show();
                }
            break;
            case PermissionHelper.PERMISSIONS_REQUEST_ACCESS_WRITE_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportTable();
                } else {
                    Toast.makeText(getContext(), getResources().getString(R.string.permission_not_granted), Toast.LENGTH_SHORT).show();
                }
            break;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private class onClickListenerMoveSubpageLeft implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (selectedSubpageNr > 0) {
                selectedSubpageNr--;
                updateOnView(OpenScale.getInstance(getContext()).getScaleMeasurementList());
            }
        }
    }

    private class onClickListenerMoveSubpageRight implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (selectedSubpageNr < (subpageView.getChildCount() - 3)) {
                selectedSubpageNr++;
                updateOnView(OpenScale.getInstance(getContext()).getScaleMeasurementList());
            }
        }
    }

    private class onClickListenerSubpageSelect implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            TextView nrView = (TextView)v;

            selectedSubpageNr = Integer.parseInt(nrView.getText().toString())-1;
            updateOnView(OpenScale.getInstance(getContext()).getScaleMeasurementList());
        }
    }

    private class ListViewAdapter extends BaseAdapter {

        private List<MeasurementView> visibleMeasurements;
        private List<ScaleMeasurement> scaleMeasurements;
        private int measurementsToShow = 0;

        private Spanned[][] stringCache;

        private ArrayList<HashMap<Integer, Spanned>> dataList;

        public void setMeasurements(List<MeasurementView> visibleMeasurements,
                                    List<ScaleMeasurement> scaleMeasurements,
                                    int maxSize) {
            this.visibleMeasurements = visibleMeasurements;
            this.scaleMeasurements = scaleMeasurements;
            measurementsToShow = Math.min(scaleMeasurements.size(), maxSize);

            stringCache = new Spanned[measurementsToShow][visibleMeasurements.size()];

            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return measurementsToShow;
        }

        @Override
        public Object getItem(int position) {
            return scaleMeasurements.get(position);
        }

        @Override
        public long getItemId(int position) {
            return scaleMeasurements.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Create entries in stringCache if needed
            if (stringCache[position][0] == null) {
                ScaleMeasurement measurement = scaleMeasurements.get(position);
                ScaleMeasurement prevMeasurement = null;
                if (position + 1 < scaleMeasurements.size()) {
                    prevMeasurement = scaleMeasurements.get(position + 1);
                }

                for (int i = 0; i < visibleMeasurements.size(); ++i) {
                    visibleMeasurements.get(i).loadFrom(measurement, prevMeasurement);

                    SpannableStringBuilder string = new SpannableStringBuilder();
                    string.append(visibleMeasurements.get(i).getValueAsString());
                    visibleMeasurements.get(i).appendDiffValue(string);

                    stringCache[position][i] = string;
                }
            }

            // Create view if needed
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(getContext());

                final int screenSize = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
                final boolean isSmallScreen =
                        screenSize != Configuration.SCREENLAYOUT_SIZE_XLARGE
                        && screenSize != Configuration.SCREENLAYOUT_SIZE_LARGE;

                for (int i = 0; i < visibleMeasurements.size(); ++i) {
                    TextView column = new TextView(getContext());
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1);
                    layoutParams.width = 0;
                    column.setLayoutParams(layoutParams);
                    column.setMinLines(2);
                    column.setGravity(Gravity.CENTER_HORIZONTAL);

                    if (isSmallScreen) {
                        column.setTextSize(COMPLEX_UNIT_DIP, 9);
                    }
                    row.addView(column);
                }
            }
            else {
                row = (LinearLayout) convertView;
            }

            // Fill view with data
            for (int i = 0; i < visibleMeasurements.size(); ++i) {
                TextView column = (TextView) row.getChildAt(i);
                column.setText(stringCache[position][i]);
            }

            return row;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
