package lab.prada.collage.component;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.thuytrinh.multitouchlistener.MultiTouchListener;

public class PhotoView extends ImageView implements BaseComponent {

	public interface OnPhotoListener {
		void onModifyPhoto(PhotoView view);
		void onBringPhotoToTop(PhotoView view);
		void onPushPhotoToBottom(PhotoView view);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		android.util.Log.d("DEBUG", "onAttachedToWindow : " + toString());
	}

	private OnPhotoListener listener;


	public PhotoView(Context context) {
		super(context);
		setOnTouchListener(new MultiTouchListener());
	}

	public PhotoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnTouchListener(new MultiTouchListener());
	}

	public PhotoView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setOnTouchListener(new MultiTouchListener());
	}

	public void setImage(Bitmap bitmap){
		setImageBitmap(bitmap);
	}
	
	public void setListener(OnPhotoListener listener){
		this.listener = listener;
		this.setOnTouchListener(new MultiTouchListener(
				new GestureListener()));
	}

	private class GestureListener extends
			GestureDetector.SimpleOnGestureListener {

		@Override
		public boolean onDown(MotionEvent e) {
			return true;
		}

		//處理圖片單擊事件，確定使用者是單擊而非雙擊的第一下時，才會觸發的事件（注意：onSingleTapUp則是只要是第一下點擊都會觸發）
		@Override
		public boolean onSingleTapConfirmed(MotionEvent e){
			if(listener != null)
				listener.onBringPhotoToTop(PhotoView.this);
			return true;
		}

		//處理圖片長按事件
		@Override
		public void onLongPress(MotionEvent e){
			if(listener != null)
				listener.onPushPhotoToBottom(PhotoView.this);
		}

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			if (listener != null)
				listener.onModifyPhoto(PhotoView.this);
			return true;
		}
	}

	@Override
	public View getView() {
		return this;
	}

	@Override
	public void setXY(int x, int y) {
		ViewCompat.setX(this, x);
		ViewCompat.setY(this, y);
	}
}
