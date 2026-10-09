package com.keralatechreach.mobigpt.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.keralatechreach.mobigpt.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the onboarding ViewPager2
 */
public class OnboardingPagerAdapter extends RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder> {
    
    private final List<OnboardingPage> pages;
    
    public OnboardingPagerAdapter(android.content.Context context) {
        pages = new ArrayList<>();
        
        // Page 1: Welcome & Offline Privacy
        pages.add(new OnboardingPage(
            R.drawable.ic_onboarding_welcome,
            "100% Private, On-Device AI",
            "Run state-of-the-art language models directly on your phone. No internet required, zero data collection, and complete privacy."
        ));
        
        // Page 2: Model Selection & Speed
        pages.add(new OnboardingPage(
            R.drawable.ic_onboarding_download,
            "Blazing Fast & Lightweight",
            "Get started instantly with Qwen 2.5 0.5B (under 400MB) for fast everyday tasks, or download larger models for deep reasoning and code."
        ));
        
        // Page 3: Chat & Features
        pages.add(new OnboardingPage(
            R.drawable.ic_onboarding_chat,
            "Powerful Offline Chat",
            "Enjoy rich markdown responses, one-tap quick actions (copy, share, star, regenerate), and organized conversation history—all stored securely on your device."
        ));
    }
    
    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_onboarding_page, parent, false);
        return new OnboardingViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        OnboardingPage page = pages.get(position);
        holder.bind(page);
    }
    
    @Override
    public int getItemCount() {
        return pages.size();
    }
    
    /**
     * ViewHolder for onboarding pages
     */
    static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final TextView titleView;
        private final TextView descriptionView;
        
        public OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.onboarding_image);
            titleView = itemView.findViewById(R.id.onboarding_title);
            descriptionView = itemView.findViewById(R.id.onboarding_description);
        }
        
        public void bind(OnboardingPage page) {
            imageView.setImageResource(page.imageRes);
            titleView.setText(page.title);
            descriptionView.setText(page.description);
            
            // Add subtle fade-in animation
            imageView.setAlpha(0f);
            titleView.setAlpha(0f);
            descriptionView.setAlpha(0f);
            
            imageView.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(100)
                .start();
            
            titleView.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(250)
                .start();
            
            descriptionView.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(400)
                .start();
        }
    }
    
    /**
     * Data class for onboarding page content
     */
    private static class OnboardingPage {
        final int imageRes;
        final String title;
        final String description;
        
        OnboardingPage(int imageRes, String title, String description) {
            this.imageRes = imageRes;
            this.title = title;
            this.description = description;
        }
    }
}
