package lab.prada.collage;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;
import lab.prada.collage.component.BaseLabelView;
import lab.prada.collage.component.BaseLabelView.OnLabelListener;
import lab.prada.collage.component.ComponentFactory;
import lab.prada.collage.component.PhotoView;
import lab.prada.collage.component.PhotoView.OnPhotoListener;
import lab.prada.collage.util.CameraImageHelper;
import lab.prada.collage.util.CollageUtils;
import lab.prada.collage.util.GlassesDetector;
import lab.prada.collage.util.StoreImageHelper;
import lab.prada.collage.util.StoreImageHelper.onSaveListener;

public class MainActivity extends BaseActivity implements OnLabelListener, OnPhotoListener,
															   View.OnClickListener {

	private static final int SELECT_PHOTO = 0;
	private static final int ADD_NEW_TEXT = 1;
	private static final int MODIFY_TEXT = 2;
	private static final int MODIFY_PHOTO = 3;

	private ProgressDialog progressDialog;
	private ViewGroup allViews;
	private ViewGroup textPanel;
	private ViewGroup photoPanel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
		getSupportActionBar().setDisplayHomeAsUpEnabled(false);

		findViewById(R.id.btnAddPic).setOnClickListener(this);
		findViewById(R.id.btnAddText).setOnClickListener(this);
		allViews = (ViewGroup) findViewById(R.id.frame);
		textPanel = (ViewGroup) findViewById(R.id.frame_texts);
		photoPanel = (ViewGroup) findViewById(R.id.frame_images);
	}

	private void showProgressDialog(boolean enable) {
		if (enable) {
			progressDialog = ProgressDialog.show(this, getResources()
					.getString(R.string.progress_title), getResources()
					.getString(R.string.progress_message), true);
		} else {
			if (progressDialog != null) {
				progressDialog.dismiss();
				progressDialog = null;
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode != RESULT_OK) {
			super.onActivityResult(requestCode, resultCode, intent);
			return;
		}
		switch (requestCode) {
			case SELECT_PHOTO:
				ArrayList<String> paths = intent.getStringArrayListExtra(MultipleImagePickerActivity.EXTRA_IMAGE_PICKER_IMAGE_PATH);
				if (paths.isEmpty()) {
					return;
				}
				List<CollageUtils.ScrapTransform> trans = CollageUtils.generateScrapsTransform(photoPanel.getWidth(), photoPanel.getHeight(), paths.size());
				clearImages();
				int i = 0;
				final int baseWidth = photoPanel.getWidth() / 2;
				for (CollageUtils.ScrapTransform t : trans) {
					final String path = paths.get(i++);
					final CollageUtils.ScrapTransform transform = t;
					Task.callInBackground(new Callable<Bitmap>() {
						@Override
						public Bitmap call() throws Exception {
							return CameraImageHelper.checkAndRotatePhoto(path);
						}
					}).onSuccess(new Continuation<Bitmap, PhotoView>() {
						@Override
						public PhotoView then(Task<Bitmap> task) throws Exception {
							PhotoView iv = ComponentFactory.create(ComponentFactory.COMPONENT_IMAGE,
																   MainActivity.this, photoPanel);
							iv.setListener(MainActivity.this);
							Bitmap bitmap = task.getResult();
							// angle
							ViewCompat.setRotation(iv, transform.rotation);
							float scale = 1f * baseWidth / bitmap.getWidth();
							ViewCompat.setScaleX(iv, scale);
							ViewCompat.setScaleY(iv, scale);
							iv.setImageBitmap(bitmap);
							iv.setXY(transform.centerX, transform.centerY);
							photoPanel.addView(iv);
							return iv;
						}
					}, Task.UI_THREAD_EXECUTOR);
				}
				photoPanel.invalidate();
				break;
			case MODIFY_PHOTO:
				if (currentSelectedImage == null) {
					return;
				}
				try {
					Bitmap bitmap = CameraImageHelper.checkAndRotatePhoto(getRealPathFromURI(intent.getData()));
					if(bitmap != null){
						currentSelectedImage.setImage(bitmap);
					}
				} catch (IOException e) {}
				currentSelectedImage = null;
				break;
			case ADD_NEW_TEXT:
				String text = intent.getStringExtra(TextEditorActivity.EXTRA_EDITOR_TEXT);
				if (text == null || text.trim() == null)
					return;
				int color = intent.getIntExtra(TextEditorActivity.EXTRA_EDITOR_COLOR, Color.BLACK);
				boolean hasStroke = intent.getBooleanExtra(TextEditorActivity.EXTRA_EDITOR_BORDER, false);
				addTextView(text, color, hasStroke);
				break;
			case MODIFY_TEXT:
				if (currentSelectedText == null) {
					return;
				}
				String txt = intent.getStringExtra(TextEditorActivity.EXTRA_EDITOR_TEXT);
				if (txt == null) {
					return;
				}
				currentSelectedText.setText(txt,
					intent.getIntExtra(TextEditorActivity.EXTRA_EDITOR_COLOR, Color.BLACK),
					intent.getBooleanExtra(TextEditorActivity.EXTRA_EDITOR_BORDER, false));
				currentSelectedText = null;
				break;
			default:
				super.onActivityResult(requestCode, resultCode, intent);
		}
	}

	private String getRealPathFromURI(Uri contentURI) {
	    Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
	    if (cursor == null) { // Source is Dropbox or other similar local file path
	        return contentURI.getPath();
	    } else {
	        cursor.moveToFirst();
	        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
	        return cursor.getString(idx);
	    }
	}

	private void clearImages() {
		photoPanel.removeAllViews();
	}

	@SuppressLint("NewApi")
	private void addTextView(String text, int color, boolean hasStroke) {
		BaseLabelView tv = ComponentFactory.create(ComponentFactory.COMPONENT_LABEL, this, textPanel);
		tv.setListener(this);
		tv.setXY(textPanel.getWidth() / 2, textPanel.getHeight() / 2);
		tv.setText(text, color, hasStroke);
		textPanel.addView(tv.getView());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_save:
				showProgressDialog(true);
				StoreImageHelper.save(getContentResolver(), allViews,
				  new onSaveListener() {
					  @Override
					  public void onSaveSuccess() {
						  showProgressDialog(false);
					  }

					  @Override
					  public void onSaveFail() {
						  showProgressDialog(false);
					  }
				  });
				return true;
			case R.id.action_magic:
				/*
				 * showProgressDialog(true); new Thread(new Runnable() {
				 *
				 * @Override public void run() {
				 */
				((ViewGroup) findViewById(R.id.frame_sticks)).removeAllViews();
				GlassesDetector detector = new GlassesDetector(MainActivity.this,
															   (ViewGroup) findViewById(R.id.frame_sticks));
				detector.detectFaces((ViewGroup) findViewById(R.id.frame_images));
				/*
				 * showProgressDialog(false); } }).start();
				 */
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private BaseLabelView currentSelectedText = null;
	private PhotoView currentSelectedImage = null;

	@Override
	public void onModifyLabel(BaseLabelView view, String text, int color, boolean hasStroke) {
		currentSelectedText = view;
		startActivityForResult(new Intent(this, TextEditorActivity.class)
		   .putExtra(TextEditorActivity.EXTRA_EDITOR_TEXT, text)
		   .putExtra(TextEditorActivity.EXTRA_EDITOR_COLOR, color)
		   .putExtra(TextEditorActivity.EXTRA_EDITOR_BORDER, hasStroke)
		   .putExtra(TextEditorActivity.EXTRA_EDITOR_TYPE, TextEditorActivity.TYPE_UPDATE), MODIFY_TEXT);
	}

	@Override
	public void onBackPressed() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.dialog_exit_title)
				.setMessage(R.string.dialog_exit_message)
				.setPositiveButton(R.string.dialog_exit_ok,
						new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int arg1) {
								dialog.dismiss();
								finish();
							}

						})
				.setNegativeButton(R.string.dialog_exit_cancel,
						new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						}).show();
	}

	@Override
	public void onModifyPhoto(PhotoView view) {
		currentSelectedImage = view;
		Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
		photoPickerIntent.setType("image/*");
		startActivityForResult(photoPickerIntent, MODIFY_PHOTO);
	}

	@Override
	public void onBringPhotoToTop(final PhotoView view) {
		/*-----debug時再參考的數據，之後刪除-----
		int[] img_coordinates = new int[2];
		view.getLocationOnScreen(img_coordinates);
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		System.out.println("density:" + metrics.density);
		System.out.println("getScale:" + view.getScaleX());
		System.out.println("getX:" + img_coordinates[0]);
		System.out.println("getPivotX:"+view.getPivotX());
		System.out.println("getWidth:"+view.getWidth());
		System.out.println("getMeasureWidth:"+view.getMeasuredWidth());
		System.out.println("getY:" + img_coordinates[1]);
		System.out.println("getPivotY:"+view.getPivotY());
		System.out.println("getHeight:"+view.getHeight());
		System.out.println("getMeasureHeight:"+view.getMeasuredHeight());
		*/
		final long periodTime = 200;
		doMainViewAnimation(view, periodTime, 1.0f, 1.15f, 1.0f, 1.15f);	//先跑動畫
		view.postDelayed(new Runnable() {	//延遲一段時間，等到跑完動畫再將圖片移到最上面
			@Override
			public void run() {
				view.bringToFront();
			}
		}, periodTime*2);

	}

	@Override
	public void onPushPhotoToBottom(final PhotoView view) {
		int[] viewPosition = new int[2];
		view.getLocationOnScreen(viewPosition);
		final float mainView_centerX = viewPosition[0] + (view.getWidth() * view.getScaleX()) / 2;	//點擊中的圖片的中心點X座標
		final float mainView_centerY = viewPosition[1] + (view.getHeight() * view.getScaleY()) / 2;	//點擊中的圖片的中心點Y座標
		final long periodTime = 300;
		int totalViewNum = photoPanel.getChildCount();	//取得目前圖片張數

		if(totalViewNum > 1) {
			view.bringToFront();	//先將欲移到最底部的圖片置頂
			//再慢慢將每張圖片都置頂，達成選中的圖片移到底部的效果
			for (int counter = 0; counter < totalViewNum-1; counter++) {
				if (isViewOverLapping(view, photoPanel.getChildAt(0))) {	//判斷是否周圍有其他圖片疊在選中的圖上
					System.out.println("overlap!");
					doOtherViewsAnimation(photoPanel.getChildAt(0), periodTime, mainView_centerX, mainView_centerY);
				}else{
					System.out.println("Nothing happen!");
				}
				photoPanel.getChildAt(0).bringToFront();
			}

			doMainViewAnimation(view, periodTime, 1.0f, 0.8f, 1.0f, 0.8f);	//執行被選中的圖片的動畫
		}
	}

	private boolean havePermission = false;

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btnAddPic:
				checkPermissions();	//檢查是否擁有相關權限
				if(havePermission) {
					startActivityForResult(new Intent(this, MultipleImagePickerActivity.class), SELECT_PHOTO);
				}
				break;
			case R.id.btnAddText:
				startActivityForResult(new Intent(this, TextEditorActivity.class), ADD_NEW_TEXT);
				break;
		}
	}

	private boolean isViewOverLapping(View mainView, View otherView){
		//目前這個方法在旋轉時會誤判（多判），應該是因為旋轉過後的Rect會變成是能包住旋轉玩的View，變得比期望中的還大，照成誤判
		Rect mainViewRect = new Rect();
		mainView.getHitRect(mainViewRect);

		Rect otherViewRect = new Rect();
		otherView.getHitRect(otherViewRect);

		System.out.println(mainViewRect);
		return (Rect.intersects(mainViewRect, otherViewRect));

		/*
		int[] mainViewPosition = new int[2];
		int[] otherViewPosition = new int[2];
		mainView.getLocationOnScreen(mainViewPosition);
		otherView.getLocationOnScreen(otherViewPosition);

		float mainViewWidth = mainView.getWidth() * mainView.getScaleX();
		float mainViewHeight = mainView.getHeight() * mainView.getScaleY();
		float otherViewWidth = otherView.getWidth() * otherView.getScaleX();
		float otherViewHeight = otherView.getHeight() * otherView.getScaleY();
		float mainViewX = mainViewPosition[0];
		float mainViewY = mainViewPosition[1];
		float otherViewX = otherViewPosition[0];
		float otherViewY = otherViewPosition[1];

		if( (mainViewX > otherViewX) && (mainViewX - otherViewX < otherViewWidth) ){	//otherView在X軸上是疊在mainView的左邊
			if( (mainViewY > otherViewY) && (mainViewY - otherViewY < otherViewHeight) ){	//otherView與mainView左上方重疊
				return true;
			}else if ( (otherViewY > mainViewY) && (otherViewY - mainViewY < mainViewHeight) ){	//otherView與mainView左下方重疊
				return true;
			}
		}else if ( (otherViewX > mainViewX) && (otherViewX - mainViewX < mainViewWidth) ){	//otherView在X軸上是疊在mainView的右邊
			if( (mainViewY > otherViewY) && (mainViewY - otherViewY < otherViewHeight) ){	//otherView與mainView右上方重疊
				return true;
			}else if ( (otherViewY > mainViewY) && (otherViewY - mainViewY < mainViewHeight) ){	//otherView與mainView右下方重疊
				return true;
			}
		}

		return false;
		*/
		/*
		int distanceX = (mainView.getWidth() + otherView.getWidth())/2;
		int distanceY = (mainView.getHeight() + otherView.getHeight())/2;
		float mainView_centerX = mainView.getX() + mainView.getWidth()/2;
		float mainView_centerY = mainView.getY() + mainView.getHeight()/2;
		float otherView_centerX = otherView.getX() + otherView.getWidth()/2;
		float otherView_centerY = otherView.getY() + otherView.getHeight()/2;

		if( Math.abs(mainView_centerX - otherView_centerX) <= distanceX
				&& Math.abs(mainView_centerY - otherView_centerY) <= distanceY){
			return true;
		}else{
			return false;
		}

		*/
/*
		int[] mainViewPosition = new int[2];
		int[] otherVIewPosition = new int[2];
		float mainViewWidth = mainView.getMeasuredWidth() * mainView.getScaleX();
		float mainViewHeight = mainView.getMeasuredHeight() * mainView.getScaleY();
		float otherViewWidth = otherView.getMeasuredWidth() * otherView.getScaleX();
		float otherViewHeight = otherView.getMeasuredHeight() * otherView.getScaleY();

		mainView.getLocationOnScreen(mainViewPosition);
		otherView.getLocationOnScreen(otherVIewPosition);

		// Rect constructor parameters: left, top, right, bottom
		Rect rectMainView = new Rect(mainViewPosition[0], mainViewPosition[1],
				mainViewPosition[0] + (int)mainViewWidth, mainViewPosition[1] + (int)mainViewHeight);
		Rect rectOtherView = new Rect(otherVIewPosition[0], otherVIewPosition[1],
				otherVIewPosition[0] + (int)otherViewWidth, otherVIewPosition[1] + (int)otherViewHeight);
		return rectMainView.intersect(rectOtherView)||rectOtherView.intersect(rectMainView);
*/
	}

	//執行點擊物件的動畫
	private void doMainViewAnimation(View mainView, long periodTime,
									 float startSizeX, float endSizeX, float startSizeY, float endSizeY){
		AnimationSet animationSet = new AnimationSet(true);
		ScaleAnimation scaleAnimation = new ScaleAnimation(startSizeX, endSizeX, startSizeY, endSizeY,
				Animation.RELATIVE_TO_PARENT, 0.5f, Animation.RELATIVE_TO_PARENT, 0.5f);
		scaleAnimation.setDuration(periodTime);
		scaleAnimation.setRepeatCount(1);
		scaleAnimation.setRepeatMode(Animation.REVERSE);	//搭配setRepeatCount(1)達成來回效果
		animationSet.addAnimation(scaleAnimation);
		mainView.startAnimation(animationSet);
	}

	//執行周遭物件的動畫
	private void doOtherViewsAnimation(View otherView, long periodTime, float mainView_centerX, float mainView_centerY){
		int[] otherViewPosition = new int[2];
		otherView.getLocationOnScreen(otherViewPosition);
		float otherView_centerX = otherViewPosition[0]+ (otherView.getWidth() * otherView.getScaleX()) / 2;	//圖片的中心點X座標
		float otherView_centerY = otherViewPosition[1] + (otherView.getHeight() * otherView.getScaleY()) / 2;	//圖片的中心點X座標
		float distanceX = 250, distanceY = 250;

		if(mainView_centerX - otherView_centerX > 0){	//周圍的view在被長按的view的左邊，往左移
			distanceX = -distanceX;
		}

		if(mainView_centerY - otherView_centerY > 0){	//周圍的view在被長按的view的上面，往上移
			distanceY = -distanceY;
		}

		AnimationSet animationSet = new AnimationSet(true);
		TranslateAnimation translateAnimation = new TranslateAnimation(0, distanceX, 0, distanceY);
		translateAnimation.setDuration(periodTime);
		translateAnimation.setRepeatCount(1);
		translateAnimation.setRepeatMode(Animation.REVERSE);	//搭配setRepeatCount(1)達成來回效果
		animationSet.addAnimation(translateAnimation);
		otherView.startAnimation(animationSet);
	}

	private static final int REQ_PERMISSIONS = 0;

	private void checkPermissions() {
		//Toast.makeText(this, "askPermission Start!", Toast.LENGTH_SHORT).show();
		String[] permissions = {
				android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
				android.Manifest.permission.READ_EXTERNAL_STORAGE
		};

		Set<String> permissionsRequest = new HashSet<>();
		for (String permission : permissions) {
			int result = ContextCompat.checkSelfPermission(this, permission);
			if (result != PackageManager.PERMISSION_GRANTED) {
				//System.out.println("permission need to be granted: "+ result);
				permissionsRequest.add(permission);
			}
		}

		if (!permissionsRequest.isEmpty()) {	//還沒取得權限，發請權限請求
			ActivityCompat.requestPermissions(this,
					permissionsRequest.toArray(new String[permissionsRequest.size()]),
					REQ_PERMISSIONS);
		}else{	//已取得過權限
			havePermission = true;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		switch (requestCode) {
			case REQ_PERMISSIONS:
				String text = "";
				for (int i = 0; i < grantResults.length; i++) {
					if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
						text += permissions[i] + "\n";
					}
				}
				if (!text.isEmpty()) {	//使用者拒絕給予權限，無法執行後續動作
					text += "No Granted,\nSome function will failure!";
					Toast.makeText(this, text, Toast.LENGTH_LONG).show();
				}else{	//使用者給予權限，打開相片選擇器
					havePermission = true;
					startActivityForResult(new Intent(this, MultipleImagePickerActivity.class), SELECT_PHOTO);
				}
				break;
		}
	}
}
