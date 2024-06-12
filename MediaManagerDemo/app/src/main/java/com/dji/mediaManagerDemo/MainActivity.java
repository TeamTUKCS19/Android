package com.dji.mediaManagerDemo;

import static java.sql.Types.NULL;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import dji.common.airlink.PhysicalSource;
import dji.common.camera.SettingsDefinitions;
import dji.common.camera.StorageState;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightMode;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.flyzone.FlyZoneInformation;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.midware.data.model.P3.DataOsdGetPushCommon;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTask;
import dji.sdk.media.FetchMediaTaskContent;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import dji.sdk.flighthub.model.*;


public class MainActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getName();
    private double droneLocationLat = 45, droneLocationLng = 45;
    private Marker marker;
    private FlightController mFlightController = null;
    private Button mBackBtn, mDeleteBtn, mReloadBtn, mDownloadBtn, mStatusBtn;
    private Button mPlayBtn, mResumeBtn, mPauseBtn, mStopBtn, mMoveToBtn;
    private RecyclerView listView;
    private FileListAdapter mListAdapter;
    private List<MediaFile> mediaFileList = new ArrayList<MediaFile>();
    private MediaManager mMediaManager;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler scheduler;
    private ProgressDialog mLoadingDialog;
    private ProgressDialog mDownloadDialog;
    private SlidingDrawer mPushDrawerSd;
    public File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/video");
    public Uri droneuri;
    public File dronefile;
    private int currentProgress = -1;
    private ImageView mDisplayImageView;
    private int lastClickViewIndex = -1;
    private View lastClickView;
    private TextView mPushTv;
    private SettingsDefinitions.StorageLocation storageLocation;

    private GoogleMap gMap;

    public LatLng latlng;
    public LatLng pos;

    public double latitude;
    public double longtitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();

        IntentFilter filter = new IntentFilter();
        filter.addAction(DemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        DemoApplication.getAircraftInstance().getCamera().setStorageStateCallBack(new StorageState.Callback() {
            @Override
            public void onUpdate(@NonNull @NotNull StorageState storageState) {
                if (storageState.isInserted()) {
                    storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;
                    DemoApplication.getAircraftInstance().getCamera().setStorageLocation(SettingsDefinitions.StorageLocation.INTERNAL_STORAGE, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                        }
                    });
                }
            }
        });

        initFlightController();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object
    }

    @Override
    protected void onResume() {
        super.onResume();
        initMediaManager();
        initFlightController();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lastClickView = null;
        if (mMediaManager != null) {
            mMediaManager.stop(null);
            mMediaManager.removeFileListStateCallback(this.updateFileListStateListener);
            mMediaManager.removeMediaUpdatedVideoPlaybackStateListener(updatedVideoPlaybackStateListener);
            mMediaManager.exitMediaDownloading();
            if (scheduler != null) {
                scheduler.removeAllTasks();
            }
        }

        if (DemoApplication.getCameraInstance() != null) {
            if (isMavicAir2() || isAir2S() || isM300()) {
                DemoApplication.getCameraInstance().exitPlayback(djiError -> {
                    if (djiError != null) {
                        DemoApplication.getCameraInstance().setFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE, djiError1 -> {
                            if (djiError1 != null) {
                                setResultToToast("Set PHOTO_SINGLE Mode Failed. " + djiError1.getDescription());
                            }
                        });
                    }
                });
            } else {
                DemoApplication.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                    if (djiError != null) {
                        setResultToToast("Set SHOOT_PHOTO Mode Failed. " + djiError.getDescription());
                    }
                });
            }
        }

        if (mediaFileList != null) {
            mediaFileList.clear();
        }
        unregisterReceiver(mReceiver);
    }

    void initUI() {


        //Init RecyclerView
        listView = (RecyclerView) findViewById(R.id.filelistView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(MainActivity.this, RecyclerView.VERTICAL, false);
        listView.setLayoutManager(layoutManager);

        //Init FileListAdapter
        mListAdapter = new FileListAdapter();
        listView.setAdapter(mListAdapter);

        //Init Loading Dialog
        mLoadingDialog = new ProgressDialog(MainActivity.this);
        mLoadingDialog.setMessage("Please wait");
        mLoadingDialog.setCanceledOnTouchOutside(false);
        mLoadingDialog.setCancelable(false);

        //Init Download Dialog
        mDownloadDialog = new ProgressDialog(MainActivity.this);
        mDownloadDialog.setTitle("Downloading file");
        mDownloadDialog.setIcon(android.R.drawable.ic_dialog_info);
        mDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDownloadDialog.setCanceledOnTouchOutside(false);
        mDownloadDialog.setCancelable(true);
        mDownloadDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mMediaManager != null) {
                    mMediaManager.exitMediaDownloading();
                }
            }
        });

        mPushDrawerSd = (SlidingDrawer) findViewById(R.id.pointing_drawer_sd);
        mPushTv = (TextView) findViewById(R.id.pointing_push_tv);
        mBackBtn = (Button) findViewById(R.id.back_btn);
        mDeleteBtn = (Button) findViewById(R.id.delete_btn);
        mDownloadBtn = (Button) findViewById(R.id.download_btn);
        mReloadBtn = (Button) findViewById(R.id.reload_btn);
        mStatusBtn = (Button) findViewById(R.id.status_btn);
        mPlayBtn = (Button) findViewById(R.id.play_btn);
        mResumeBtn = (Button) findViewById(R.id.resume_btn);
        mPauseBtn = (Button) findViewById(R.id.pause_btn);
        mStopBtn = (Button) findViewById(R.id.stop_btn);
        mMoveToBtn = (Button) findViewById(R.id.moveTo_btn);
        mDisplayImageView = (ImageView) findViewById(R.id.imageView);
        mDisplayImageView.setVisibility(View.VISIBLE);

        mBackBtn.setOnClickListener(this);
        mDeleteBtn.setOnClickListener(this);
        mDownloadBtn.setOnClickListener(this);
        mReloadBtn.setOnClickListener(this);
        mDownloadBtn.setOnClickListener(this);
        mStatusBtn.setOnClickListener(this);
        mPlayBtn.setOnClickListener(this);
        mResumeBtn.setOnClickListener(this);
        mPauseBtn.setOnClickListener(this);
        mStopBtn.setOnClickListener(this);
        mMoveToBtn.setOnClickListener(this);

    }

    private void showProgressDialog() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (mLoadingDialog != null) {
                    mLoadingDialog.show();
                }
            }
        });
    }

    private void hideProgressDialog() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (null != mLoadingDialog && mLoadingDialog.isShowing()) {
                    mLoadingDialog.dismiss();
                }
            }
        });
    }

    private void ShowDownloadProgressDialog() {
        if (mDownloadDialog != null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.incrementProgressBy(-mDownloadDialog.getProgress());
                    mDownloadDialog.show();
                }
            });
        }
    }

    private void HideDownloadProgressDialog() {
        if (null != mDownloadDialog && mDownloadDialog.isShowing()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.dismiss();
                }
            });
        }
    }

    private void setResultToToast(final String result) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setResultToText(final String string) {
        if (mPushTv == null) {
            setResultToToast("Push info tv has not be init...");
        }
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPushTv.setText(string);
            }
        });
    }

    private void initMediaManager() {
        if (DemoApplication.getProductInstance() == null) {
            mediaFileList.clear();
            mListAdapter.notifyDataSetChanged();
            DJILog.e(TAG, "Product disconnected");
            return;
        } else {
            if (null != DemoApplication.getCameraInstance() && DemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                mMediaManager = DemoApplication.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    mMediaManager.addMediaUpdatedVideoPlaybackStateListener(this.updatedVideoPlaybackStateListener);
                    if (isMavicAir2() || isAir2S() || isM300()) {
                        DemoApplication.getCameraInstance().enterPlayback(djiError -> {
                            if (djiError == null) {
                                DJILog.e(TAG, "Set cameraMode success");
                                showProgressDialog();
                                getFileList();
                            } else {
                                setResultToToast("Set cameraMode failed");
                            }
                        });
                    } else {
                        DemoApplication.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, error -> {
                            if (error == null) {
                                DJILog.e(TAG, "Set cameraMode success");
                                showProgressDialog();
                                getFileList();
                            } else {
                                setResultToToast("Set cameraMode failed");
                            }
                        });
                    }

                    if (mMediaManager.isVideoPlaybackSupported()) {
                        DJILog.e(TAG, "Camera support video playback!");
                    } else {
                        setResultToToast("Camera does not support video playback!");
                    }
                    scheduler = mMediaManager.getScheduler();
                }

            } else if (null != DemoApplication.getCameraInstance()
                    && !DemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                setResultToToast("Media Download Mode not Supported");
            }
        }
        return;
    }

    private void getFileList() {
        mMediaManager = DemoApplication.getCameraInstance().getMediaManager();
        if (mMediaManager != null) {

            if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)) {
                DJILog.e(TAG, "Media Manager is busy.");
            } else {
                mMediaManager.refreshFileListOfStorageLocation(storageLocation, djiError -> {
                    if (null == djiError) {
                        hideProgressDialog();

                        //Reset data
                        if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                            mediaFileList.clear();
                            lastClickViewIndex = -1;
                            lastClickView = null;
                        }

                        List<MediaFile> tempList;
                        if (storageLocation == SettingsDefinitions.StorageLocation.SDCARD) {
                            tempList = mMediaManager.getSDCardFileListSnapshot();
                        } else {
                            tempList = mMediaManager.getInternalStorageFileListSnapshot();
                        }
                        if (tempList != null) {
                            mediaFileList.addAll(tempList);
                        }
                        if (mediaFileList != null) {
                            Collections.sort(mediaFileList, (lhs, rhs) -> {
                                if (lhs.getTimeCreated() < rhs.getTimeCreated()) {
                                    return 1;
                                } else if (lhs.getTimeCreated() > rhs.getTimeCreated()) {
                                    return -1;
                                }
                                return 0;
                            });
                        }
                        scheduler.resume(error -> {
                            if (error == null) {
                                getThumbnails();
                            }
                        });
                    } else {
                        hideProgressDialog();
                        setResultToToast("Get Media File List Failed:" + djiError.getDescription());
                    }
                });
            }
        }
    }

    private void getThumbnails() {
        if (mediaFileList.size() <= 0) {
            setResultToToast("No File info for downloading thumbnails");
            return;
        }
        for (int i = 0; i < mediaFileList.size(); i++) {
            getThumbnailByIndex(i);
        }
    }

    private FetchMediaTask.Callback taskCallback = new FetchMediaTask.Callback() {
        @Override
        public void onUpdate(MediaFile file, FetchMediaTaskContent option, DJIError error) {
            if (null == error) {
                if (option == FetchMediaTaskContent.PREVIEW) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mListAdapter.notifyDataSetChanged();
                        }
                    });
                }
                if (option == FetchMediaTaskContent.THUMBNAIL) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            } else {
                DJILog.e(TAG, "Fetch Media Task Failed" + error.getDescription());
            }
        }
    };

    private void getThumbnailByIndex(final int index) {
        FetchMediaTask task = new FetchMediaTask(mediaFileList.get(index), FetchMediaTaskContent.THUMBNAIL, taskCallback);
        scheduler.moveTaskToEnd(task);
    }

    private static class ItemHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail_img;
        TextView file_name;
        TextView file_type;
        TextView file_size;
        TextView file_time;

        public ItemHolder(View itemView) {
            super(itemView);
            this.thumbnail_img = (ImageView) itemView.findViewById(R.id.filethumbnail);
            this.file_name = (TextView) itemView.findViewById(R.id.filename);
            this.file_type = (TextView) itemView.findViewById(R.id.filetype);
            this.file_size = (TextView) itemView.findViewById(R.id.fileSize);
            this.file_time = (TextView) itemView.findViewById(R.id.filetime);
        }
    }

    private class FileListAdapter extends RecyclerView.Adapter<ItemHolder> {
        @Override
        public int getItemCount() {
            if (mediaFileList != null) {
                return mediaFileList.size();
            }
            return 0;
        }

        @Override
        public ItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.media_info_item, parent, false);
            return new ItemHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemHolder mItemHolder, final int index) {

            final MediaFile mediaFile = mediaFileList.get(index);
            if (mediaFile != null) {
                if (mediaFile.getMediaType() != MediaFile.MediaType.MOV && mediaFile.getMediaType() != MediaFile.MediaType.MP4) {
                    mItemHolder.file_time.setVisibility(View.GONE);
                } else {
                    mItemHolder.file_time.setVisibility(View.VISIBLE);
                    mItemHolder.file_time.setText(mediaFile.getDurationInSeconds() + " s");
                }
                mItemHolder.file_name.setText(mediaFile.getFileName());
                mItemHolder.file_type.setText(mediaFile.getMediaType().name());
                mItemHolder.file_size.setText(mediaFile.getFileSize() + " Bytes");
                mItemHolder.thumbnail_img.setImageBitmap(mediaFile.getThumbnail());
                mItemHolder.thumbnail_img.setOnClickListener(ImgOnClickListener);
                mItemHolder.thumbnail_img.setTag(mediaFile);
                mItemHolder.itemView.setTag(index);

                if (lastClickViewIndex == index) {
                    mItemHolder.itemView.setSelected(true);
                } else {
                    mItemHolder.itemView.setSelected(false);
                }
                mItemHolder.itemView.setOnClickListener(itemViewOnClickListener);

            }
        }
    }

    private View.OnClickListener itemViewOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            lastClickViewIndex = (int) (v.getTag());

            if (lastClickView != null && lastClickView != v) {
                lastClickView.setSelected(false);
            }
            v.setSelected(true);
            lastClickView = v;
        }
    };

    private View.OnClickListener ImgOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MediaFile selectedMedia = (MediaFile) v.getTag();
            if (selectedMedia != null && mMediaManager != null) {
                addMediaTask(selectedMedia);
            }
        }
    };

    private void addMediaTask(final MediaFile mediaFile) {
        final FetchMediaTaskScheduler scheduler = mMediaManager.getScheduler();
        final FetchMediaTask task =
                new FetchMediaTask(mediaFile, FetchMediaTaskContent.PREVIEW, new FetchMediaTask.Callback() {
                    @Override
                    public void onUpdate(final MediaFile mediaFile, FetchMediaTaskContent fetchMediaTaskContent, DJIError error) {
                        if (null == error) {
                            if (mediaFile.getPreview() != null) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        final Bitmap previewBitmap = mediaFile.getPreview();
                                        mDisplayImageView.setVisibility(View.VISIBLE);
                                        mDisplayImageView.setImageBitmap(previewBitmap);
                                    }
                                });
                            } else {
                                setResultToToast("null bitmap!");
                            }
                        } else {
                            setResultToToast("fetch preview image failed: " + error.getDescription());
                        }
                    }
                });

        scheduler.resume(error -> {
            if (error == null) {
                scheduler.moveTaskToNext(task);
            } else {
                setResultToToast("resume scheduler failed: " + error.getDescription());
            }
        });
    }

    //Listeners
    private MediaManager.FileListStateListener updateFileListStateListener = state -> currentFileListState = state;

    private MediaManager.VideoPlaybackStateListener updatedVideoPlaybackStateListener =
            new MediaManager.VideoPlaybackStateListener() {
                @Override
                public void onUpdate(MediaManager.VideoPlaybackState videoPlaybackState) {
                    updateStatusTextView(videoPlaybackState);
                }
            };

    private void updateStatusTextView(MediaManager.VideoPlaybackState videoPlaybackState) {
        final StringBuffer pushInfo = new StringBuffer();

        addLineToSB(pushInfo, "Video Playback State", null);
        if (videoPlaybackState != null) {
            if (videoPlaybackState.getPlayingMediaFile() != null) {
                addLineToSB(pushInfo, "media index", videoPlaybackState.getPlayingMediaFile().getIndex());
                addLineToSB(pushInfo, "media size", videoPlaybackState.getPlayingMediaFile().getFileSize());
                addLineToSB(pushInfo,
                        "media duration",
                        videoPlaybackState.getPlayingMediaFile().getDurationInSeconds());
                addLineToSB(pushInfo, "media created date", videoPlaybackState.getPlayingMediaFile().getDateCreated());
                addLineToSB(pushInfo,
                        "media orientation",
                        videoPlaybackState.getPlayingMediaFile().getVideoOrientation());
            } else {
                addLineToSB(pushInfo, "media index", "None");
            }
            addLineToSB(pushInfo, "media current position", videoPlaybackState.getPlayingPosition());
            addLineToSB(pushInfo, "media current status", videoPlaybackState.getPlaybackStatus());
            addLineToSB(pushInfo, "media cached percentage", videoPlaybackState.getCachedPercentage());
            addLineToSB(pushInfo, "media cached position", videoPlaybackState.getCachedPosition());
            pushInfo.append("\n");
            setResultToText(pushInfo.toString());
        }
    }

    private void addLineToSB(StringBuffer sb, String name, Object value) {
        if (sb == null) return;
        sb.
                append((name == null || "".equals(name)) ? "" : name + ": ").
                append(value == null ? "" : value + "").
                append("\n");
    }

    private void downloadFileByIndex(final int index) {
        if ((mediaFileList.get(index).getMediaType() == MediaFile.MediaType.PANORAMA)
                || (mediaFileList.get(index).getMediaType() == MediaFile.MediaType.SHALLOW_FOCUS)) {
            return;
        }
        String file_name = mediaFileList.get(index).getFileName();
        mediaFileList.get(index).fetchFileData(destDir, null, new DownloadListener<String>() {
            @Override
            public void onFailure(DJIError error) {
                HideDownloadProgressDialog();
                setResultToToast("Download File Failed" + error.getDescription());
                currentProgress = -1;
            }

            @Override
            public void onProgress(long total, long current) {
            }

            @Override
            public void onRateUpdate(long total, long current, long persize) {
                int tmpProgress = (int) (1.0 * current / total * 100);
                if (tmpProgress != currentProgress) {
                    mDownloadDialog.setProgress(tmpProgress);
                    currentProgress = tmpProgress;
                }
            }

            @Override
            public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {

            }

            @Override
            public void onStart() {
                currentProgress = -1;
                ShowDownloadProgressDialog();
            }

            @Override
            public void onSuccess(String filePath) {
                HideDownloadProgressDialog();
                setResultToToast("Download File Success" + ":" + filePath);
                dronefile = new File(filePath + "/" + file_name);
                droneuri = Uri.fromFile(dronefile);
                send2Server(dronefile);
                currentProgress = -1;
            }
        });
    }


    private void deleteFileByIndex(final int index) {
        ArrayList<MediaFile> fileToDelete = new ArrayList<MediaFile>();
        if (mediaFileList.size() > index) {
            fileToDelete.add(mediaFileList.get(index));
            mMediaManager.deleteFiles(fileToDelete, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
                @Override
                public void onSuccess(List<MediaFile> x, DJICameraError y) {
                    DJILog.e(TAG, "Delete file success");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            MediaFile file = mediaFileList.remove(index);

                            //Reset select view
                            lastClickViewIndex = -1;
                            lastClickView = null;

                            //Update recyclerView
                            mListAdapter.notifyItemRemoved(index);
                        }
                    });
                }

                @Override
                public void onFailure(DJIError error) {
                    setResultToToast("Delete file failed");
                }
            });
        }
    }

    private void playVideo() {
        mDisplayImageView.setVisibility(View.INVISIBLE);
        MediaFile selectedMediaFile = mediaFileList.get(lastClickViewIndex);
        if ((selectedMediaFile.getMediaType() == MediaFile.MediaType.MOV) || (selectedMediaFile.getMediaType() == MediaFile.MediaType.MP4)) {
            mMediaManager.playVideoMediaFile(selectedMediaFile, error -> {
                if (null != error) {
                    setResultToToast("Play Video Failed " + error.getDescription());
                } else {
                    DJILog.e(TAG, "Play Video Success");
                }
            });
        }
    }

    private void moveToPosition() {

        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.prompt_input_position, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(promptsView);
        final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);
        alertDialogBuilder.setCancelable(false).setPositiveButton("OK", (dialog, id) -> {
                    String ms = userInput.getText().toString();
                    mMediaManager.moveToPosition(Integer.parseInt(ms),
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {
                                    if (null != error) {
                                        setResultToToast("Move to video position failed" + error.getDescription());
                                    } else {
                                        DJILog.e(TAG, "Move to video position successfully.");
                                    }
                                }
                            });
                })
                .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }

    public void send2Server(File file){

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(MultipartBody.FORM, file))
                .addFormDataPart("latitude", "3")
                .addFormDataPart("longitude" , "4")
                .addFormDataPart("altitude" , "5")
                .build();
        Request request = new Request.Builder()
                .url("http://13.209.231.12:9900/upload_video") // Server URL 은 본인 IP를 입력
                .post(requestBody)
                .build();


        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                setResultToToast("failure failed");
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                setResultToToast("send...");
                Log.d("TEST : ", response.body().string());

            }
        });
    }


    public void uploadVideo(Uri videoUri) {
        OkHttpClient client = new OkHttpClient();

        // ContentResolver를 사용하여 URI로부터 파일을 읽어옴
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File videoFile = new File(dir, "Screen_recordings/Screen_Recording_20240321-145008_MediaManagerDemo");

        MediaType mediaType = MediaType.parse("video/mp4"); // 동영상 파일의 MIME 타입 설정
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("video", videoFile.getName(), RequestBody.create(mediaType, videoFile))
                .build();

        Request request = new Request.Builder()
                .url("http://13.209.231.12:9900/upload_video") // 플라스크 서버의 엔드포인트 URL로 수정
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    setResultToToast("success");
                } else {
                    setResultToToast("failed");
                }
            }

            @Override

            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                setResultToToast("fail failed");
            }
        });
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back_btn: {
                this.finish();
                break;
            }
            case R.id.delete_btn: {
                deleteFileByIndex(lastClickViewIndex);
                break;
            }
            case R.id.reload_btn: {
                getFileList();
                break;
            }
            case R.id.download_btn: {
                downloadFileByIndex(lastClickViewIndex);
                break;
            }
            case R.id.status_btn: {
                if (mPushDrawerSd.isOpened()) {
                    mPushDrawerSd.animateClose();
                } else {
                    mPushDrawerSd.animateOpen();
                }
                break;
            }
            case R.id.play_btn: {
                playVideo();
                break;
            }
            case R.id.resume_btn: {
                mMediaManager.resume(error -> {
                    if (null != error) {
                        setResultToToast("Resume Video Failed" + error.getDescription());
                    } else {
                        DJILog.e(TAG, "Resume Video Success");
                    }
                });
                break;
            }
            case R.id.pause_btn: {
                /*mMediaManager.pause(error -> {
                    if (null != error) {
                        setResultToToast("Pause Video Failed" + error.getDescription());
                    } else {
                        DJILog.e(TAG, "Pause Video Success");
                    }
                });*/
                updateDroneLocation();
                cameraUpdate();
                break;
            }
            case R.id.stop_btn: {
                /*mMediaManager.stop(error -> {
                    if (null != error) {
                        setResultToToast("Stop Video Failed" + error.getDescription());
                    } else {
                        DJILog.e(TAG, "Stop Video Success");
                    }
                });*/
                //initFlightController();
                //updateDroneLocation();
                printSurroundFlyZones();
                latlng = new LatLng(DataOsdGetPushCommon.getInstance().getLatitude(),
                        DataOsdGetPushCommon.getInstance().getLongitude());
                if (latlng != null) {
                    //Create MarkerOptions object
                    final MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(latlng);
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
                    marker = gMap.addMarker(markerOptions);
                }
                gMap.moveCamera(CameraUpdateFactory.newLatLng(latlng));
                gMap.animateCamera(CameraUpdateFactory.zoomTo(15.0f));

                setResultToToast("위도" + latitude);
                setResultToToast("경도" + longtitude);

                //getLocation(Flight);
                break;
            }
            case R.id.moveTo_btn: {
                send2Server(dronefile);
                //uploadVideo(sendVd);
                break;
            }
            default:
                break;
        }
    }
    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightController();
    }
    private boolean isFlightControllerSupported() {
        return DJISDKManager.getInstance().getProduct() != null &&
                DJISDKManager.getInstance().getProduct() instanceof Aircraft &&
                ((Aircraft) DJISDKManager.getInstance().getProduct()).getFlightController() != null;
    }

    private void initFlightController() {

        if (isFlightControllerSupported()) {
            mFlightController = ((Aircraft) DJISDKManager.getInstance().getProduct()).getFlightController();
            mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(FlightControllerState
                                             djiFlightControllerCurrentState) {
                    if (gMap != null) {
                        droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                        droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                        updateDroneLocation();
                    }
                }
            });
        }
    }
    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void updateDroneLocation(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (marker != null) {
                    marker.remove();
                }
                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {

                    pos = new LatLng(droneLocationLat, droneLocationLng);

                    //Create MarkerOptions object
                    final MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(pos);
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
                    marker = gMap.addMarker(markerOptions);
                }
            }
        });
    }
    @Override
    public void onMapClick(com.google.android.gms.maps.model.LatLng latLng) {
        if(marker != null){
            marker.remove();
        }
        marker = gMap.addMarker(new MarkerOptions().position(latLng));
        LatLng markerLatLng = latLng;
        gMap.moveCamera(CameraUpdateFactory.newLatLng(markerLatLng)); // 카메라 변경

        latitude = markerLatLng.latitude;
        longtitude = markerLatLng.longitude;
    }

    public void onMapReady(GoogleMap googleMap) {
        LatLng paloAlto = new LatLng(37.3401906, 126.7335293); // 기준점

        gMap = googleMap;

        gMap.addMarker(new MarkerOptions().position(paloAlto).title("Marker in here"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(paloAlto));
        gMap.animateCamera(CameraUpdateFactory.zoomTo(17.0f));
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            gMap.setMyLocationEnabled(true);
            return;
        }
        gMap.setOnMapClickListener(this);
        printSurroundFlyZones();

    }
    private void printSurroundFlyZones() {

        DJISDKManager.getInstance().getFlyZoneManager().getFlyZonesInSurroundingArea(new CommonCallbacks.CompletionCallbackWith<ArrayList<FlyZoneInformation>>() {
            @Override
            public void onSuccess(ArrayList<FlyZoneInformation> flyZones) {
                setResultToText("get surrounding Fly Zone Success!");
                showSurroundFlyZonesInTv(flyZones);
            }

            @Override
            public void onFailure(DJIError error) {
                setResultToText(error.getDescription());
            }
        });
    }

    private void showSurroundFlyZonesInTv(final List<FlyZoneInformation> flyZones) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                StringBuffer sb = new StringBuffer();
                for (FlyZoneInformation flyZone : flyZones) {
                    if (flyZone != null && flyZone.getCategory() != null){

                        sb.append("FlyZoneId: ").append(flyZone.getFlyZoneID()).append("\n");
                        sb.append("Category: ").append(flyZone.getCategory().name()).append("\n");
                        sb.append("Latitude: ").append(flyZone.getCoordinate().getLatitude()).append("\n");
                        sb.append("Longitude: ").append(flyZone.getCoordinate().getLongitude()).append("\n");
                        sb.append("FlyZoneType: ").append(flyZone.getFlyZoneType().name()).append("\n");
                        sb.append("Radius: ").append(flyZone.getRadius()).append("\n");
                        sb.append("Shape: ").append(flyZone.getShape().name()).append("\n");
                        sb.append("StartTime: ").append(flyZone.getStartTime()).append("\n");
                        sb.append("EndTime: ").append(flyZone.getEndTime()).append("\n");
                        sb.append("UnlockStartTime: ").append(flyZone.getUnlockStartTime()).append("\n");
                        sb.append("UnlockEndTime: ").append(flyZone.getUnlockEndTime()).append("\n");
                        sb.append("Name: ").append(flyZone.getName()).append("\n");
                        sb.append("\n");
                    }
                }

            }

        });

    }
    public void getLocation(double A, double B) {
        DecimalFormat df = new DecimalFormat("#.##");
        String formatted = df.format(A);
        String formatteds = df.format(B);

        setResultToToast("위도 : " + formatteds);
        setResultToToast("경도 : " + formatted);
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);
        setResultToToast("위도" + pos.latitude);
        setResultToToast("경도" + pos.longitude);
    }

    private boolean isMavicAir2() {
        BaseProduct baseProduct = DemoApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MAVIC_AIR_2;
        }
        return false;
    }

    private boolean isAir2S() {
        BaseProduct baseProduct = DemoApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.DJI_AIR_2S;
        }
        return false;
    }

    private boolean isM300() {
        BaseProduct baseProduct = DemoApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MATRICE_300_RTK;
        }
        return false;
    }
}
