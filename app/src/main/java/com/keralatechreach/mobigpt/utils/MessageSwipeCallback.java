package com.keralatechreach.mobigpt.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.keralatechreach.mobigpt.R;

/**
 * Custom swipe callback for message items
 * - Swipe right: Star/unstar message (green background)
 * - Swipe left: Delete message (red background)
 */
public class MessageSwipeCallback extends ItemTouchHelper.SimpleCallback {
    
    private final Context context;
    private final SwipeActionListener listener;
    private final Paint paint;
    private final ColorDrawable backgroundLeft;
    private final ColorDrawable backgroundRight;
    private final Drawable deleteIcon;
    private final Drawable starIcon;
    private final int iconMargin;
    
    // Colors for swipe backgrounds
    private static final int COLOR_DELETE = 0xFFEF4444; // Red
    private final int colorStar; // Dynamic accent color
    
    public interface SwipeActionListener {
        void onSwipeToStar(int position);
        void onSwipeToDelete(int position);
    }
    
    public MessageSwipeCallback(Context context, SwipeActionListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.context = context;
        this.listener = listener;
        this.paint = new Paint();
        
        // Get accent color from theme
        ThemeManager themeManager = new ThemeManager(context);
        colorStar = ContextCompat.getColor(context, themeManager.getPrimaryColorResId());
        
        // Initialize backgrounds
        backgroundLeft = new ColorDrawable(COLOR_DELETE);
        backgroundRight = new ColorDrawable(colorStar);
        
        // Load icons
        deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete);
        starIcon = ContextCompat.getDrawable(context, R.drawable.ic_star_filled);
        
        // Tint icons white
        if (deleteIcon != null) {
            deleteIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        }
        if (starIcon != null) {
            starIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        }
        
        iconMargin = (int) (16 * context.getResources().getDisplayMetrics().density);
    }
    
    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, 
                         @NonNull RecyclerView.ViewHolder target) {
        return false; // We don't support drag-and-drop
    }
    
    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        
        // Haptic feedback
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        
        if (direction == ItemTouchHelper.RIGHT) {
            // Swipe right - Star message
            listener.onSwipeToStar(position);
        } else if (direction == ItemTouchHelper.LEFT) {
            // Swipe left - Delete message
            listener.onSwipeToDelete(position);
        }
    }
    
    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, 
                           @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, 
                           int actionState, boolean isCurrentlyActive) {
        
        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();
        
        // Don't draw anything if not swiping
        if (dX == 0f && !isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            return;
        }
        
        if (dX > 0) {
            // Swiping right - Show green background with star icon
            backgroundRight.setBounds(
                itemView.getLeft(),
                itemView.getTop(),
                itemView.getLeft() + (int) dX,
                itemView.getBottom()
            );
            backgroundRight.draw(c);
            
            // Draw star icon on the left side
            if (starIcon != null) {
                int iconSize = (int) (24 * context.getResources().getDisplayMetrics().density);
                int iconTop = itemView.getTop() + (itemHeight - iconSize) / 2;
                int iconLeft = itemView.getLeft() + iconMargin;
                int iconRight = iconLeft + iconSize;
                int iconBottom = iconTop + iconSize;
                
                starIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                starIcon.draw(c);
            }
            
        } else if (dX < 0) {
            // Swiping left - Show red background with delete icon
            backgroundLeft.setBounds(
                itemView.getRight() + (int) dX,
                itemView.getTop(),
                itemView.getRight(),
                itemView.getBottom()
            );
            backgroundLeft.draw(c);
            
            // Draw delete icon on the right side
            if (deleteIcon != null) {
                int iconSize = (int) (24 * context.getResources().getDisplayMetrics().density);
                int iconTop = itemView.getTop() + (itemHeight - iconSize) / 2;
                int iconRight = itemView.getRight() - iconMargin;
                int iconLeft = iconRight - iconSize;
                int iconBottom = iconTop + iconSize;
                
                deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                deleteIcon.draw(c);
            }
        }
        
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
    
    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // Don't allow swiping on status messages or streaming messages
        // You can add more sophisticated logic here based on view type
        return super.getSwipeDirs(recyclerView, viewHolder);
    }
    
    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        // Require 40% swipe to trigger action (easier to swipe)
        return 0.4f;
    }
    
    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        // Make it easier to complete swipe with velocity
        return defaultValue * 0.5f;
    }
    
    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        // Lower threshold for swipe velocity
        return defaultValue * 0.5f;
    }
}
