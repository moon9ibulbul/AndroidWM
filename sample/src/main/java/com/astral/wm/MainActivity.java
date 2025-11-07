/*
 *    Copyright 2018 Yizheng Huang
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.astral.wm;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.media.MediaScannerConnection;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.watermark.androidwm.Watermark;
import com.watermark.androidwm.WatermarkDetector;
import com.watermark.androidwm.bean.WatermarkPosition;
import com.watermark.androidwm.task.DetectionReturnValue;
import com.watermark.androidwm.listener.BuildFinishListener;
import com.watermark.androidwm.WatermarkBuilder;
import com.watermark.androidwm.bean.WatermarkImage;
import com.watermark.androidwm.bean.WatermarkText;
import com.watermark.androidwm.listener.DetectFinishListener;

import timber.log.Timber;
//import com.watermark.androidwm.utils.BitmapUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the sample for library: androidwm.
 *
 * @author huangyz0918 (huangyz0918@gmail.com)
 * @since 29/08/2018
 */
public class MainActivity extends AppCompatActivity {

    private RadioButton mode_single;
    private RadioButton mode_tile;
    private RadioButton mode_invisible;
    private LinearLayout invisibleMethodsLayout;
    private RadioGroup invisibleGroup;
    private RadioButton modeInvisibleLsb;
    private RadioButton modeInvisibleFd;
    private Button btnAddText;
    private Button btnAddImg;
    private Button btnChooseBackground;
    private Button btnChooseWatermark;
    private Button btnDetectImage;
    private Button btnDetectText;
    private Button btnClear;
    private Button btnBulkImage;
    private Button btnSaveOutput;

    private ImageView backgroundView;
    private ImageView watermarkView;
    private Bitmap watermarkBitmap;
    private TextView placeholderView;
    private TextView positionLabel;

    private EditText editText;

    private ProgressBar progressBar;
    private Spinner positionSpinner;

    private PlacementOption selectedPlacement = PlacementOption.CENTER;

    private ActivityResultLauncher<String[]> pickBackgroundLauncher;
    private ActivityResultLauncher<String[]> pickWatermarkLauncher;
    private ActivityResultLauncher<String[]> bulkBackgroundLauncher;

    private static final String[] IMAGE_MIME_TYPES = new String[]{"image/*"};
    private static final String OUTPUT_DIRECTORY = "AndroidWM";
    private static final String OUTPUT_FILE_PREFIX = "watermark_";
    private static final PlacementOption[] SMART_EVALUATION_ORDER = {
            PlacementOption.TOP_LEFT,
            PlacementOption.TOP_CENTER,
            PlacementOption.TOP_RIGHT,
            PlacementOption.CENTER_LEFT,
            PlacementOption.CENTER,
            PlacementOption.CENTER_RIGHT,
            PlacementOption.BOTTOM_LEFT,
            PlacementOption.BOTTOM_CENTER,
            PlacementOption.BOTTOM_RIGHT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initImagePickers();
        setContentView(R.layout.activity_main);
        initViews();
        initEvents();
    }

    private void initViews() {
        mode_single = findViewById(R.id.mode_single);
        mode_tile = findViewById(R.id.mode_tile);
        mode_invisible = findViewById(R.id.mode_invisible);
        btnAddImg = findViewById(R.id.btn_add_image);
        btnAddText = findViewById(R.id.btn_add_text);
        btnChooseBackground = findViewById(R.id.btn_choose_background);
        btnChooseWatermark = findViewById(R.id.btn_choose_watermark);
        btnBulkImage = findViewById(R.id.btn_bulk_image);
        btnDetectImage = findViewById(R.id.btn_detect_image);
        btnDetectText = findViewById(R.id.btn_detect_text);
        btnClear = findViewById(R.id.btn_clear_watermark);
        btnSaveOutput = findViewById(R.id.btn_save_output);

        invisibleMethodsLayout = findViewById(R.id.lay_invisible_methods);
        invisibleGroup = findViewById(R.id.group_invisible_methods);
        modeInvisibleLsb = findViewById(R.id.mode_invisible_lsb);
        modeInvisibleFd = findViewById(R.id.mode_invisible_fd);

        editText = findViewById(R.id.editText);
        backgroundView = findViewById(R.id.imageView);
        watermarkView = findViewById(R.id.imageView_watermark);
        placeholderView = findViewById(R.id.text_placeholder);
        positionLabel = findViewById(R.id.label_watermark_position);
        positionSpinner = findViewById(R.id.spinner_watermark_position);

        progressBar = findViewById(R.id.progressBar);

        setupPositionSpinner();
        resetBackground();
        loadDefaultWatermark();
        updateInvisibleOptions();
        updatePlacementAvailability();
    }

    private void initEvents() {
        // The sample method of adding a text watermark.
        btnAddText.setOnClickListener((View v) -> {
            String markText = editText.getText().toString();
            if(markText.trim().isEmpty()){
                Toast.makeText(this, R.string.toast_enter_text_first, Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap backgroundBitmap = extractBitmapFromImageView(backgroundView);
            if (backgroundBitmap == null) {
                Toast.makeText(this, R.string.toast_no_background, Toast.LENGTH_SHORT).show();
                return;
            }
            if (mode_invisible.isChecked()) {
                createInvisibleTextMark();
                return;
            }
            WatermarkText watermarkText = new WatermarkText(markText)
                    .setPosition(new WatermarkPosition(0.5, 0.5))
                    .setOrigin(new WatermarkPosition(0.5, 0.5))
                    .setTextSize(40)
                    .setTextAlpha(255)
                    .setTextColor(Color.WHITE)
                    .setTextFont(R.font.champagne)
                    .setTextShadow(0.1f, 5, 5, Color.BLUE);

            WatermarkBuilder.create(this, backgroundView)
                    .setTileMode(mode_tile.isChecked())
                    .loadWatermarkText(watermarkText)
                    .getWatermark()
                    .setToImageView(backgroundView);
            showPlaceholder(false);
        });

        // The sample method of adding an image watermark.
        btnAddImg.setOnClickListener((View v) -> {
            if (watermarkBitmap == null) {
                Toast.makeText(this, R.string.toast_watermark_not_ready, Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap backgroundBitmap = extractBitmapFromImageView(backgroundView);
            if (backgroundBitmap == null) {
                Toast.makeText(this, R.string.toast_no_background, Toast.LENGTH_SHORT).show();
                return;
            }
            if (mode_invisible.isChecked()) {
                createInvisibleImgMark();
                return;
            }

            WatermarkImage watermarkImage = createWatermarkImage(watermarkBitmap, backgroundBitmap, selectedPlacement);
            if (watermarkImage == null) {
                Toast.makeText(this, R.string.toast_watermark_not_ready, Toast.LENGTH_SHORT).show();
                return;
            }

            WatermarkBuilder
                    .create(this, backgroundView)
                    .loadWatermarkImage(watermarkImage)
                    .setTileMode(mode_tile.isChecked())
                    .getWatermark()
                    .setToImageView(backgroundView);
            showPlaceholder(false);

        });

        btnChooseBackground.setOnClickListener(v -> pickBackgroundLauncher.launch(IMAGE_MIME_TYPES));

        btnChooseWatermark.setOnClickListener(v -> pickWatermarkLauncher.launch(IMAGE_MIME_TYPES));

        btnBulkImage.setOnClickListener(v -> {
            if (mode_invisible.isChecked()) {
                Toast.makeText(this, R.string.toast_bulk_mode_not_supported, Toast.LENGTH_SHORT).show();
                return;
            }
            if (watermarkBitmap == null) {
                Toast.makeText(this, R.string.toast_watermark_not_ready, Toast.LENGTH_SHORT).show();
                return;
            }
            bulkBackgroundLauncher.launch(IMAGE_MIME_TYPES);
        });

        btnSaveOutput.setOnClickListener(v -> saveCurrentResult());

        // detect the text watermark.
        btnDetectText.setOnClickListener((View v) -> {
            Bitmap detectionBitmap = prepareDetectionBitmap();
            if (detectionBitmap == null) {
                Toast.makeText(this, R.string.toast_no_background, Toast.LENGTH_SHORT).show();
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            WatermarkDetector.create(detectionBitmap, isLsbSelected())
                    .detect(new DetectFinishListener() {
                        @Override
                        public void onSuccess(DetectionReturnValue returnValue) {
                            progressBar.setVisibility(View.GONE);
                            if (returnValue != null) {
                                Toast.makeText(MainActivity.this,
                                        getString(R.string.toast_detect_text_success,
                                                getSelectedMethodLabel(),
                                                returnValue.getWatermarkString()),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(String message) {
                            progressBar.setVisibility(View.GONE);
                            Timber.e(message);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // detect the image watermark.
        btnDetectImage.setOnClickListener((View v) -> {
            Bitmap detectionBitmap = prepareDetectionBitmap();
            if (detectionBitmap == null) {
                Toast.makeText(this, R.string.toast_no_background, Toast.LENGTH_SHORT).show();
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            WatermarkDetector.create(detectionBitmap, isLsbSelected())
                    .detect(new DetectFinishListener() {
                        @Override
                        public void onSuccess(DetectionReturnValue returnValue) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.toast_detect_image_success, getSelectedMethodLabel()),
                                    Toast.LENGTH_SHORT).show();
                            if (returnValue != null) {
                                watermarkView.setVisibility(View.VISIBLE);
                                watermarkView.setImageBitmap(returnValue.getWatermarkBitmap());
                            }
                        }

                        @Override
                        public void onFailure(String message) {
                            progressBar.setVisibility(View.GONE);
                            Timber.e(message);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // reload the background.
        btnClear.setOnClickListener((View v) -> clearBackgroundImage());

        View.OnClickListener modeListener = v -> updateInvisibleOptions();
        mode_single.setOnClickListener(modeListener);
        mode_tile.setOnClickListener(modeListener);
        mode_invisible.setOnClickListener(modeListener);

    }

    private void createInvisibleImgMark(){
        progressBar.setVisibility(View.VISIBLE);
        WatermarkBuilder
                .create(this, backgroundView)
                .loadWatermarkImage(watermarkBitmap)
                .setInvisibleWMListener(isLsbSelected(), new BuildFinishListener<Bitmap>() {
                    @Override
                    public void onSuccess(Bitmap object) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this,
                                getString(R.string.toast_create_image_success, getSelectedMethodLabel()),
                                Toast.LENGTH_SHORT).show();
                        if (object != null) {
                            backgroundView.setImageBitmap(object);
                            showPlaceholder(false);
                            // Save to local needs permission.
//                                BitmapUtils.saveAsPNG(object, "sdcard/DCIM/", true);
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        progressBar.setVisibility(View.GONE);
                        Timber.e(message);
                    }
                });
    }

    private void createInvisibleTextMark(){
        progressBar.setVisibility(View.VISIBLE);
        WatermarkText watermarkText = new WatermarkText(editText.getText().toString());
        WatermarkBuilder
                .create(this, backgroundView)
                .loadWatermarkText(watermarkText)
                .setInvisibleWMListener(isLsbSelected(), new BuildFinishListener<Bitmap>() {
                    @Override
                    public void onSuccess(Bitmap object) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this,
                                getString(R.string.toast_create_text_success, getSelectedMethodLabel()),
                                Toast.LENGTH_SHORT).show();
                        if (object != null) {
                            backgroundView.setImageBitmap(object);
                            showPlaceholder(false);
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        progressBar.setVisibility(View.GONE);
                        Timber.e(message);
                    }
                });
    }
    private boolean isLsbSelected() {
        if (invisibleGroup == null) {
            return true;
        }
        int checkedId = invisibleGroup.getCheckedRadioButtonId();
        return checkedId == R.id.mode_invisible_lsb;
    }

    private String getSelectedMethodLabel() {
        int checkedId = invisibleGroup != null ? invisibleGroup.getCheckedRadioButtonId() : View.NO_ID;
        if (checkedId == R.id.mode_invisible_fd) {
            return getString(R.string.label_invisible_fd);
        }
        return getString(R.string.label_invisible_lsb);
    }

    private void updateInvisibleOptions() {
        boolean enableInvisibleOptions = mode_invisible.isChecked();
        if (invisibleMethodsLayout != null) {
            invisibleMethodsLayout.setEnabled(enableInvisibleOptions);
            invisibleMethodsLayout.setAlpha(enableInvisibleOptions ? 1f : 0.4f);
        }
        if (modeInvisibleLsb != null) {
            modeInvisibleLsb.setEnabled(enableInvisibleOptions);
        }
        if (modeInvisibleFd != null) {
            modeInvisibleFd.setEnabled(enableInvisibleOptions);
        }
        updatePlacementAvailability();
    }

    private void setupPositionSpinner() {
        if (positionSpinner == null) {
            return;
        }
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.watermark_positions, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        positionSpinner.setAdapter(adapter);
        positionSpinner.setSelection(selectedPlacement.ordinal());
        positionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PlacementOption[] values = PlacementOption.values();
                if (position >= 0 && position < values.length) {
                    selectedPlacement = values[position];
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });
    }

    private void updatePlacementAvailability() {
        boolean enablePlacement = mode_single.isChecked() && !mode_invisible.isChecked();
        if (positionSpinner != null) {
            positionSpinner.setEnabled(enablePlacement);
            positionSpinner.setAlpha(enablePlacement ? 1f : 0.4f);
        }
        if (positionLabel != null) {
            positionLabel.setEnabled(enablePlacement);
            positionLabel.setAlpha(enablePlacement ? 1f : 0.4f);
        }
    }

    private void initImagePickers() {
        pickBackgroundLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                loadBackgroundFromUri(uri);
            }
        });
        pickWatermarkLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                loadWatermarkFromUri(uri);
            }
        });
        bulkBackgroundLauncher = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                processBulkWatermarks(uris);
            }
        });
    }

    private void loadBackgroundFromUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException exception) {
            Timber.w(exception, "Unable to persist permission for background uri");
        }

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                Toast.makeText(this, R.string.toast_load_background_failed, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (IOException e) {
            Timber.e(e, "Failed to verify background image");
            Toast.makeText(this, R.string.toast_load_background_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Glide.with(this)
                .load(uri)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        showPlaceholder(true);
                        Toast.makeText(MainActivity.this, R.string.toast_load_background_failed, Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        showPlaceholder(false);
                        return false;
                    }
                })
                .into(backgroundView);
        watermarkView.setVisibility(View.GONE);
    }

    private void loadWatermarkFromUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException exception) {
            Timber.w(exception, "Unable to persist permission for watermark uri");
        }

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                Toast.makeText(this, R.string.toast_load_watermark_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                Toast.makeText(this, R.string.toast_load_watermark_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            watermarkBitmap = bitmap;
            watermarkView.setVisibility(View.VISIBLE);
            watermarkView.setImageBitmap(bitmap);
        } catch (IOException e) {
            Timber.e(e, "Failed to load watermark image");
            Toast.makeText(this, R.string.toast_load_watermark_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetBackground() {
        if (backgroundView != null) {
            backgroundView.setImageDrawable(null);
        }
        showPlaceholder(true);
    }

    private void clearBackgroundImage() {
        resetBackground();
        if (watermarkView != null) {
            watermarkView.setVisibility(View.GONE);
        }
        loadDefaultWatermark();
    }

    private void showPlaceholder(boolean show) {
        if (placeholderView != null) {
            placeholderView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void saveCurrentResult() {
        Bitmap bitmap = extractBitmapFromImageView(backgroundView);
        if (bitmap == null) {
            Toast.makeText(this, R.string.toast_no_background, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean saved = saveBitmapToGallery(this, bitmap, createOutputFileName());
        Toast.makeText(this,
                saved ? R.string.toast_save_success : R.string.toast_save_failed,
                Toast.LENGTH_SHORT).show();
    }

    private Bitmap extractBitmapFromImageView(ImageView imageView) {
        if (imageView == null || imageView.getDrawable() == null) {
            return null;
        }
        if (imageView.getDrawable() instanceof BitmapDrawable) {
            return ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        }
        return null;
    }

    private Bitmap prepareDetectionBitmap() {
        Bitmap source = extractBitmapFromImageView(backgroundView);
        if (source == null) {
            return null;
        }
        Bitmap.Config config = source.getConfig();
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        Bitmap copy = null;
        try {
            copy = source.copy(config, false);
        } catch (IllegalArgumentException exception) {
            Timber.w(exception, "Unable to copy bitmap for detection using original config");
        }
        if (copy == null && config != Bitmap.Config.ARGB_8888) {
            try {
                copy = source.copy(Bitmap.Config.ARGB_8888, false);
            } catch (IllegalArgumentException exception) {
                Timber.w(exception, "Unable to copy bitmap for detection using ARGB_8888");
            }
        }
        return copy != null ? copy : source;
    }

    private static boolean saveBitmapToGallery(Context context, Bitmap bitmap, String displayName) {
        if (context == null || bitmap == null) {
            return false;
        }

        String fileName = displayName + ".png";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + OUTPUT_DIRECTORY);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            android.content.ContentResolver resolver = context.getContentResolver();
            Uri uri = null;
            try {
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return false;
                }
                try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                    if (outputStream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        resolver.delete(uri, null, null);
                        return false;
                    }
                }
                ContentValues updateValues = new ContentValues();
                updateValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, updateValues, null, null);
                return true;
            } catch (IOException | SecurityException e) {
                if (uri != null) {
                    resolver.delete(uri, null, null);
                }
                Timber.e(e, "Failed to save image to gallery");
                return false;
            }
        } else {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File outputDir = new File(picturesDir, OUTPUT_DIRECTORY);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                Timber.e("Failed to create output directory: %s", outputDir.getAbsolutePath());
                return false;
            }
            File outputFile = new File(outputDir, fileName);
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    return false;
                }
            } catch (IOException | SecurityException e) {
                Timber.e(e, "Failed to save image to gallery");
                return false;
            }
            MediaScannerConnection.scanFile(context, new String[]{outputFile.getAbsolutePath()}, null, null);
            return true;
        }
    }

    private static String createOutputFileName() {
        return OUTPUT_FILE_PREFIX + System.currentTimeMillis() + "_" + System.nanoTime();
    }

    private WatermarkImage createWatermarkImage(Bitmap bitmap, Bitmap backgroundBitmap, PlacementOption placement) {
        if (bitmap == null || backgroundBitmap == null) {
            return null;
        }
        WatermarkImage watermarkImage = new WatermarkImage(bitmap)
                .setImageAlpha(80)
                .setRotation(15)
                .setSize(0.1);

        PlacementResult placementResult = resolvePlacement(backgroundBitmap, bitmap, watermarkImage.getSize(), placement);
        watermarkImage.setPosition(placementResult.position);
        watermarkImage.setOrigin(placementResult.origin);
        return watermarkImage;
    }

    private PlacementResult resolvePlacement(Bitmap backgroundBitmap, Bitmap watermarkBitmap,
                                             double watermarkSize, PlacementOption placement) {
        if (placement == null) {
            placement = PlacementOption.CENTER;
        }
        if (placement.hasPreset()) {
            return new PlacementResult(placement.createPosition(), placement.createOrigin());
        }
        if (placement == PlacementOption.RANDOM) {
            return createRandomPlacement(backgroundBitmap, watermarkBitmap, watermarkSize);
        }
        if (placement == PlacementOption.SMART) {
            return createSmartPlacement(backgroundBitmap, watermarkBitmap, watermarkSize);
        }
        return new PlacementResult(new WatermarkPosition(0.5, 0.5), new WatermarkPosition(0.5, 0.5));
    }

    private PlacementResult createRandomPlacement(Bitmap backgroundBitmap, Bitmap watermarkBitmap,
                                                  double watermarkSize) {
        if (backgroundBitmap == null || watermarkBitmap == null) {
            return new PlacementResult(new WatermarkPosition(0.5, 0.5), new WatermarkPosition(0.5, 0.5));
        }
        WatermarkDimensions dimensions = calculateWatermarkDimensions(backgroundBitmap, watermarkBitmap, watermarkSize);
        double marginX = Math.min(0.5, Math.max(0, dimensions.widthRatio / 2.0));
        double marginY = Math.min(0.5, Math.max(0, dimensions.heightRatio / 2.0));
        double rangeX = Math.max(0, 1 - 2 * marginX);
        double rangeY = Math.max(0, 1 - 2 * marginY);
        double positionX = rangeX > 0 ? marginX + Math.random() * rangeX : 0.5;
        double positionY = rangeY > 0 ? marginY + Math.random() * rangeY : 0.5;
        return new PlacementResult(new WatermarkPosition(positionX, positionY),
                new WatermarkPosition(0.5, 0.5));
    }

    private PlacementResult createSmartPlacement(Bitmap backgroundBitmap, Bitmap watermarkBitmap,
                                                 double watermarkSize) {
        if (backgroundBitmap == null || watermarkBitmap == null) {
            return new PlacementResult(new WatermarkPosition(0.5, 0.5), new WatermarkPosition(0.5, 0.5));
        }
        WatermarkDimensions dimensions = calculateWatermarkDimensions(backgroundBitmap, watermarkBitmap, watermarkSize);
        int markWidth = Math.max(1, (int) Math.round(backgroundBitmap.getWidth() * dimensions.widthRatio));
        int markHeight = Math.max(1, (int) Math.round(backgroundBitmap.getHeight() * dimensions.heightRatio));
        PlacementResult bestPlacement = null;
        double bestScore = Double.MAX_VALUE;

        for (PlacementOption option : SMART_EVALUATION_ORDER) {
            PlacementResult candidate = new PlacementResult(option.createPosition(), option.createOrigin());
            Rect region = calculatePlacementRect(candidate, backgroundBitmap.getWidth(), backgroundBitmap.getHeight(),
                    markWidth, markHeight);
            double score = calculateRegionScore(backgroundBitmap, region);
            if (score < bestScore) {
                bestScore = score;
                bestPlacement = candidate;
            }
        }

        if (bestPlacement == null) {
            return new PlacementResult(new WatermarkPosition(0.5, 0.5), new WatermarkPosition(0.5, 0.5));
        }
        return bestPlacement;
    }

    private Rect calculatePlacementRect(PlacementResult placement, int backgroundWidth, int backgroundHeight,
                                        int markWidth, int markHeight) {
        double anchorX = placement.position.getPositionX() * backgroundWidth;
        double anchorY = placement.position.getPositionY() * backgroundHeight;
        double offsetX = placement.origin.getPositionX() * markWidth;
        double offsetY = placement.origin.getPositionY() * markHeight;
        int left = clamp((int) Math.round(anchorX - offsetX), 0, Math.max(0, backgroundWidth - markWidth));
        int top = clamp((int) Math.round(anchorY - offsetY), 0, Math.max(0, backgroundHeight - markHeight));
        int right = Math.min(backgroundWidth, left + markWidth);
        int bottom = Math.min(backgroundHeight, top + markHeight);
        return new Rect(left, top, right, bottom);
    }

    private double calculateRegionScore(Bitmap backgroundBitmap, Rect region) {
        int regionWidth = region.width();
        int regionHeight = region.height();
        if (regionWidth <= 0 || regionHeight <= 0) {
            return Double.MAX_VALUE;
        }
        int stepX = Math.max(1, regionWidth / 10);
        int stepY = Math.max(1, regionHeight / 10);
        int columnCount = Math.max(1, (int) Math.ceil((double) regionWidth / stepX));
        int[] previousRow = new int[columnCount];
        boolean firstRow = true;
        double sum = 0;
        double sumSquares = 0;
        double gradient = 0;
        int count = 0;

        for (int y = region.top; y < region.bottom; y += stepY) {
            int previousGray = -1;
            int columnIndex = 0;
            for (int x = region.left; x < region.right; x += stepX) {
                int pixel = backgroundBitmap.getPixel(x, y);
                int gray = (int) (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));
                sum += gray;
                sumSquares += (double) gray * gray;
                if (previousGray >= 0) {
                    gradient += Math.abs(gray - previousGray);
                }
                if (!firstRow && columnIndex < previousRow.length) {
                    gradient += Math.abs(gray - previousRow[columnIndex]);
                }
                if (columnIndex < previousRow.length) {
                    previousRow[columnIndex] = gray;
                }
                previousGray = gray;
                columnIndex++;
                count++;
            }
            firstRow = false;
        }

        if (count == 0) {
            return Double.MAX_VALUE;
        }
        double mean = sum / count;
        double variance = sumSquares / count - mean * mean;
        double normalizedGradient = gradient / Math.max(1, count);
        return variance + normalizedGradient;
    }

    private WatermarkDimensions calculateWatermarkDimensions(Bitmap backgroundBitmap, Bitmap watermarkBitmap,
                                                             double watermarkSize) {
        if (backgroundBitmap == null || watermarkBitmap == null || watermarkSize <= 0) {
            return new WatermarkDimensions(0.2, 0.2);
        }
        double widthRatio = watermarkSize;
        double scale = (backgroundBitmap.getWidth() * watermarkSize) / watermarkBitmap.getWidth();
        double scaledHeight = watermarkBitmap.getHeight() * scale;
        double heightRatio = scaledHeight / backgroundBitmap.getHeight();
        return new WatermarkDimensions(widthRatio, heightRatio);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static class PlacementResult {
        final WatermarkPosition position;
        final WatermarkPosition origin;

        PlacementResult(WatermarkPosition position, WatermarkPosition origin) {
            this.position = position;
            this.origin = origin;
        }
    }

    private static class WatermarkDimensions {
        final double widthRatio;
        final double heightRatio;

        WatermarkDimensions(double widthRatio, double heightRatio) {
            this.widthRatio = widthRatio;
            this.heightRatio = heightRatio;
        }
    }

    private enum PlacementOption {
        TOP_LEFT(0.0, 0.0, 0.0, 0.0),
        TOP_CENTER(0.5, 0.0, 0.5, 0.0),
        TOP_RIGHT(1.0, 0.0, 1.0, 0.0),
        CENTER_LEFT(0.0, 0.5, 0.0, 0.5),
        CENTER(0.5, 0.5, 0.5, 0.5),
        CENTER_RIGHT(1.0, 0.5, 1.0, 0.5),
        BOTTOM_LEFT(0.0, 1.0, 0.0, 1.0),
        BOTTOM_CENTER(0.5, 1.0, 0.5, 1.0),
        BOTTOM_RIGHT(1.0, 1.0, 1.0, 1.0),
        RANDOM(),
        SMART();

        private final double posX;
        private final double posY;
        private final double originX;
        private final double originY;
        private final boolean hasPreset;

        PlacementOption(double posX, double posY, double originX, double originY) {
            this.posX = posX;
            this.posY = posY;
            this.originX = originX;
            this.originY = originY;
            this.hasPreset = true;
        }

        PlacementOption() {
            this.posX = 0.5;
            this.posY = 0.5;
            this.originX = 0.5;
            this.originY = 0.5;
            this.hasPreset = false;
        }

        boolean hasPreset() {
            return hasPreset;
        }

        WatermarkPosition createPosition() {
            return new WatermarkPosition(posX, posY);
        }

        WatermarkPosition createOrigin() {
            return new WatermarkPosition(originX, originY);
        }
    }

    private void processBulkWatermarks(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }

        if (watermarkBitmap == null) {
            Toast.makeText(this, R.string.toast_watermark_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException exception) {
                Timber.w(exception, "Unable to persist permission for bulk uri");
            }
        }

        new BulkWatermarkTask(this, new ArrayList<>(uris), watermarkBitmap, mode_tile.isChecked(), selectedPlacement)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void onBulkWatermarkCompleted(int successCount, int totalCount) {
        Toast.makeText(this,
                getString(R.string.toast_bulk_complete, successCount, totalCount),
                Toast.LENGTH_LONG)
                .show();
    }

    private Bitmap decodeBitmapFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            Timber.e(e, "Failed to decode background image for bulk watermarking");
            return null;
        }
    }

    private static class BulkWatermarkTask extends AsyncTask<Void, Void, BulkResult> {
        private final WeakReference<MainActivity> activityReference;
        private final List<Uri> backgroundUris;
        private final Bitmap watermarkBitmap;
        private final boolean tileMode;
        private final PlacementOption placementOption;

        BulkWatermarkTask(MainActivity activity, List<Uri> backgroundUris, Bitmap watermarkBitmap,
                          boolean tileMode, PlacementOption placementOption) {
            this.activityReference = new WeakReference<>(activity);
            this.backgroundUris = backgroundUris;
            this.watermarkBitmap = watermarkBitmap;
            this.tileMode = tileMode;
            this.placementOption = placementOption;
        }

        @Override
        protected void onPreExecute() {
            MainActivity activity = activityReference.get();
            if (activity != null) {
                activity.progressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected BulkResult doInBackground(Void... voids) {
            MainActivity activity = activityReference.get();
            if (activity == null) {
                return new BulkResult(0, backgroundUris.size());
            }

            int successCount = 0;
            for (Uri uri : backgroundUris) {
                if (isCancelled()) {
                    break;
                }
                Bitmap background = activity.decodeBitmapFromUri(uri);
                if (background == null) {
                    continue;
                }

                WatermarkImage watermarkImage = activity.createWatermarkImage(watermarkBitmap, background, placementOption);
                if (watermarkImage == null) {
                    continue;
                }

                try {
                    Watermark watermark = WatermarkBuilder
                            .create(activity, background)
                            .setTileMode(tileMode)
                            .loadWatermarkImage(watermarkImage)
                            .getWatermark();
                    Bitmap output = watermark.getOutputImage();
                    if (output != null) {
                        if (saveBitmapToGallery(activity, output, createOutputFileName())) {
                            successCount++;
                        }
                        output.recycle();
                    }
                } catch (Exception e) {
                    Timber.e(e, "Failed to create watermark for bulk image");
                } finally {
                    background.recycle();
                }
            }

            return new BulkResult(successCount, backgroundUris.size());
        }

        @Override
        protected void onPostExecute(BulkResult result) {
            MainActivity activity = activityReference.get();
            if (activity != null) {
                activity.progressBar.setVisibility(View.GONE);
                activity.onBulkWatermarkCompleted(result.successCount, result.totalCount);
            }
        }

        @Override
        protected void onCancelled() {
            MainActivity activity = activityReference.get();
            if (activity != null) {
                activity.progressBar.setVisibility(View.GONE);
            }
        }
    }

    private static class BulkResult {
        final int successCount;
        final int totalCount;

        BulkResult(int successCount, int totalCount) {
            this.successCount = successCount;
            this.totalCount = totalCount;
        }
    }

    private void loadDefaultWatermark() {
        watermarkBitmap = BitmapFactory.decodeResource(getResources(),
                R.drawable.test_watermark);
        if (watermarkView != null) {
            watermarkView.setVisibility(View.GONE);
        }
    }
}
