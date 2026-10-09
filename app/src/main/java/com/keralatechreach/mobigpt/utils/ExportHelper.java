package com.keralatechreach.mobigpt.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import android.media.MediaScannerConnection;
import android.os.Environment;

import com.keralatechreach.mobigpt.database.Chat;
import com.keralatechreach.mobigpt.database.ChatDatabase;
import com.keralatechreach.mobigpt.database.Message;
import com.keralatechreach.mobigpt.database.MessageDao;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Helper class for exporting conversations to various formats.
 * Uses Android native APIs (MediaStore and android.graphics.pdf.PdfDocument)
 * without requiring any proprietary or copyleft third-party libraries.
 */
public class ExportHelper {
    private static final String TAG = "ExportHelper";
    private static final String NOTIFICATION_CHANNEL_ID = "export_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Standard A4 dimensions at 72 dpi
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN_HORIZONTAL = 40f;
    private static final float MARGIN_TOP = 45f;
    private static final float MARGIN_BOTTOM = 50f;
    private static final int CONTENT_WIDTH = (int) (PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)); // 515

    // Date formatter helpers (dynamically locale-aware and thread-safe)
    private static SimpleDateFormat getFileDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
    }

    private static SimpleDateFormat getDisplayDateFormat() {
        return new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
    }

    /**
     * Functional interface for streaming export content
     */
    private interface ExportDataWriter {
        void write(OutputStream out) throws Exception;
    }

    /**
     * Unified file writer supporting Android 10+ (API 29+) MediaStore Downloads
     * and legacy Android 8.1 - 9 (API 27 - 28) external Downloads directory.
     */
    private static Uri writeExportFile(Context context, String filename, String mimeType, ExportDataWriter dataWriter) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri fileUri = resolver.insert(collection, values);

            if (fileUri == null) {
                Log.e(TAG, "Failed to create file in MediaStore Downloads");
                return null;
            }

            try (OutputStream out = resolver.openOutputStream(fileUri)) {
                dataWriter.write(out);
            }

            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(fileUri, values, null, null);

            return fileUri;
        } else {
            // Android 8.1 - 9 (API 27 - 28) legacy external storage
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                Log.w(TAG, "Could not create directory: " + downloadsDir);
            }
            File targetFile = new File(downloadsDir, filename);

            try (OutputStream out = new FileOutputStream(targetFile)) {
                dataWriter.write(out);
            }

            // Trigger media scanner so the file is immediately visible in file manager/downloads
            MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                new String[]{targetFile.getAbsolutePath()},
                new String[]{mimeType},
                null
            );

            try {
                return androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    targetFile
                );
            } catch (Exception e) {
                Log.w(TAG, "Could not obtain FileProvider URI, falling back to Uri.fromFile", e);
                return Uri.fromFile(targetFile);
            }
        }
    }

    /**
     * Export conversation to Markdown format in Downloads folder
     */
    public static Uri exportToMarkdown(Context context, Chat chat, List<Message> messages) {
        try {
            String timestamp = getFileDateFormat().format(new Date());
            String sanitizedTitle = sanitizeFilename(chat.title != null ? chat.title : "Chat");
            String filename = sanitizedTitle + "_" + timestamp + ".md";

            Uri fileUri = writeExportFile(context, filename, "text/markdown", out -> {
                try (OutputStreamWriter writer = new OutputStreamWriter(out)) {
                    writer.write("# " + (chat.title != null ? chat.title : "Chat Conversation") + "\n\n");
                    writer.write("**Exported:** " + getDisplayDateFormat().format(new Date()) + "\n\n");
                    writer.write("**Created:** " + getDisplayDateFormat().format(new Date(chat.createdAt)) + "\n\n");
                    writer.write("---\n\n");

                    for (Message message : messages) {
                        String role = message.isUser ? "**You**" : "**Assistant**";
                        String time = getDisplayDateFormat().format(new Date(message.timestamp));

                        writer.write("### " + role + "\n");
                        writer.write("*" + time + "*\n\n");
                        writer.write((message.content != null ? message.content : "") + "\n\n");
                        writer.write("---\n\n");
                    }

                    writer.write("\n*Exported from MobiGPT*\n");
                    writer.flush();
                }
            });

            if (fileUri != null) {
                Log.d(TAG, "Successfully exported to Markdown: " + filename);
                showExportNotification(context, filename, fileUri, "text/markdown");
            }
            return fileUri;

        } catch (Exception e) {
            Log.e(TAG, "Error exporting to Markdown", e);
            return null;
        }
    }

    /**
     * Export conversation to PDF format in Downloads folder using native Android PdfDocument
     */
    public static Uri exportToPDF(Context context, Chat chat, List<Message> messages) {
        try {
            String timestamp = getFileDateFormat().format(new Date());
            String sanitizedTitle = sanitizeFilename(chat.title != null ? chat.title : "Chat");
            String filename = sanitizedTitle + "_" + timestamp + ".pdf";

            Uri fileUri = writeExportFile(context, filename, "application/pdf", out -> {
                PdfDocument document = new PdfDocument();
                PdfContext ctx = new PdfContext(document);

                // Document Title
                ctx.ensureSpace(50f);
                Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                titlePaint.setColor(Color.rgb(33, 33, 33));
                titlePaint.setTextSize(20f);
                titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                titlePaint.setTextAlign(Paint.Align.CENTER);
                ctx.canvas.drawText(chat.title != null ? chat.title : "Chat Conversation",
                        PAGE_WIDTH / 2f, ctx.currentY + 20f, titlePaint);
                ctx.currentY += 30f;

                // Document Metadata
                ctx.ensureSpace(35f);
                Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                metaPaint.setColor(Color.rgb(120, 120, 120));
                metaPaint.setTextSize(9f);
                metaPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                metaPaint.setTextAlign(Paint.Align.CENTER);
                String metaText = "Exported: " + getDisplayDateFormat().format(new Date()) +
                        "  |  Created: " + getDisplayDateFormat().format(new Date(chat.createdAt));
                ctx.canvas.drawText(metaText, PAGE_WIDTH / 2f, ctx.currentY + 10f, metaPaint);
                ctx.currentY += 25f;

                // Horizontal Header Divider
                drawDivider(ctx);

                // Messages
                if (messages != null) {
                    for (int i = 0; i < messages.size(); i++) {
                        drawMessage(ctx, messages.get(i));
                        if (i < messages.size() - 1) {
                            drawDivider(ctx);
                        }
                    }
                }

                ctx.finish();
                document.writeTo(out);
                document.close();
            });

            if (fileUri != null) {
                Log.d(TAG, "Successfully exported to PDF: " + filename);
                showExportNotification(context, filename, fileUri, "application/pdf");
            }
            return fileUri;

        } catch (Exception e) {
            Log.e(TAG, "Error exporting to PDF", e);
            return null;
        }
    }

    /**
     * Export all chats to a single PDF file in Downloads folder using native Android PdfDocument
     */
    public static Uri exportAllChatsToPDF(Context context) {
        try {
            ChatDatabase database = ChatDatabase.getDatabase(context);
            List<Chat> allChats = database.chatDao().getAllChats();

            if (allChats == null || allChats.isEmpty()) {
                Log.w(TAG, "No chats to export");
                return null;
            }

            String timestamp = getFileDateFormat().format(new Date());
            String filename = "MobiGPT_AllChats_" + timestamp + ".pdf";

            Uri fileUri = writeExportFile(context, filename, "application/pdf", out -> {
                PdfDocument document = new PdfDocument();
                PdfContext ctx = new PdfContext(document);

                // Document Title
                ctx.ensureSpace(50f);
                Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                titlePaint.setColor(Color.rgb(33, 33, 33));
                titlePaint.setTextSize(22f);
                titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                titlePaint.setTextAlign(Paint.Align.CENTER);
                ctx.canvas.drawText("MobiGPT - All Conversations", PAGE_WIDTH / 2f, ctx.currentY + 22f, titlePaint);
                ctx.currentY += 32f;

                // Document Metadata
                ctx.ensureSpace(35f);
                Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                metaPaint.setColor(Color.rgb(120, 120, 120));
                metaPaint.setTextSize(9f);
                metaPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                metaPaint.setTextAlign(Paint.Align.CENTER);
                String metaText = "Exported: " + getDisplayDateFormat().format(new Date()) +
                        "  |  Total Conversations: " + allChats.size();
                ctx.canvas.drawText(metaText, PAGE_WIDTH / 2f, ctx.currentY + 10f, metaPaint);
                ctx.currentY += 25f;

                drawDivider(ctx);

                MessageDao messageDao = database.messageDao();
                for (int chatIndex = 0; chatIndex < allChats.size(); chatIndex++) {
                    Chat chat = allChats.get(chatIndex);
                    List<Message> chatMessages = messageDao.getMessagesForChat(chat.id);

                    if (chatMessages == null || chatMessages.isEmpty()) {
                        continue;
                    }

                    drawChatSectionHeader(ctx, chatIndex, chat.title, chat.createdAt, chatMessages.size());

                    for (int i = 0; i < chatMessages.size(); i++) {
                        drawMessage(ctx, chatMessages.get(i));
                        if (i < chatMessages.size() - 1) {
                            drawDivider(ctx);
                        }
                    }

                    if (chatIndex < allChats.size() - 1) {
                        ctx.ensureSpace(30f);
                        Paint sectionDivider = new Paint(Paint.ANTI_ALIAS_FLAG);
                        sectionDivider.setColor(Color.rgb(180, 180, 180));
                        sectionDivider.setStrokeWidth(1.5f);
                        ctx.canvas.drawLine(MARGIN_HORIZONTAL, ctx.currentY + 10f,
                                PAGE_WIDTH - MARGIN_HORIZONTAL, ctx.currentY + 10f, sectionDivider);
                        ctx.currentY += 25f;
                    }
                }

                ctx.finish();
                document.writeTo(out);
                document.close();
            });

            if (fileUri != null) {
                Log.d(TAG, "Successfully exported all chats to PDF: " + filename);
                showExportNotification(context, filename, fileUri, "application/pdf");
            }
            return fileUri;

        } catch (Exception e) {
            Log.e(TAG, "Error exporting all chats to PDF", e);
            return null;
        }
    }

    /**
     * Draw chat header banner for multi-chat export
     */
    private static void drawChatSectionHeader(PdfContext ctx, int chatIndex, String title, long createdAt, int msgCount) {
        ctx.ensureSpace(55f);

        // Header Background
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.rgb(244, 244, 246));
        ctx.canvas.drawRect(MARGIN_HORIZONTAL, ctx.currentY,
                PAGE_WIDTH - MARGIN_HORIZONTAL, ctx.currentY + 44f, bgPaint);

        // Header Title
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.rgb(33, 33, 33));
        titlePaint.setTextSize(12f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String titleText = "Conversation " + (chatIndex + 1) + ": " + (title != null ? title : "Untitled Chat");
        ctx.canvas.drawText(titleText, MARGIN_HORIZONTAL + 10f, ctx.currentY + 18f, titlePaint);

        // Header Subtitle
        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.rgb(110, 110, 110));
        subPaint.setTextSize(8.5f);
        subPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        String subText = "Created: " + getDisplayDateFormat().format(new Date(createdAt)) + "  |  Messages: " + msgCount;
        ctx.canvas.drawText(subText, MARGIN_HORIZONTAL + 10f, ctx.currentY + 34f, subPaint);

        ctx.currentY += 52f;
    }

    /**
     * Render a single user or AI message with role badge, timestamp, and wrapped text
     */
    private static void drawMessage(PdfContext ctx, Message message) {
        String role = message.isUser ? "You" : "Assistant";
        int roleColor = message.isUser ? Color.rgb(33, 150, 243) : Color.rgb(76, 175, 80);

        ctx.ensureSpace(35f);

        // Role title
        Paint rolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rolePaint.setColor(roleColor);
        rolePaint.setTextSize(12f);
        rolePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        ctx.canvas.drawText(role, MARGIN_HORIZONTAL, ctx.currentY + 12f, rolePaint);

        // Timestamp
        float roleWidth = rolePaint.measureText(role);
        Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setColor(Color.rgb(140, 140, 140));
        timePaint.setTextSize(8f);
        timePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        String timeText = "  •  " + getDisplayDateFormat().format(new Date(message.timestamp));
        ctx.canvas.drawText(timeText, MARGIN_HORIZONTAL + roleWidth, ctx.currentY + 12f, timePaint);

        ctx.currentY += 18f;

        // Message body
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(35, 35, 35));
        textPaint.setTextSize(9.5f);

        int textIndent = 8;
        int textWidth = CONTENT_WIDTH - textIndent;
        String content = message.content != null ? message.content : "";
        StaticLayout layout = createStaticLayout(content, textPaint, textWidth);

        // Draw line-by-line to gracefully handle multi-page message wrapping
        for (int i = 0; i < layout.getLineCount(); i++) {
            float lineTop = layout.getLineTop(i);
            float lineBottom = layout.getLineBottom(i);
            float lineH = lineBottom - lineTop;

            ctx.ensureSpace(lineH);

            ctx.canvas.save();
            ctx.canvas.clipRect(MARGIN_HORIZONTAL + textIndent, ctx.currentY,
                    MARGIN_HORIZONTAL + textIndent + textWidth, ctx.currentY + lineH);
            ctx.canvas.translate(MARGIN_HORIZONTAL + textIndent, ctx.currentY - lineTop);
            layout.draw(ctx.canvas);
            ctx.canvas.restore();

            ctx.currentY += lineH;
        }

        ctx.currentY += 10f;
    }

    /**
     * Draw subtle divider line between messages
     */
    private static void drawDivider(PdfContext ctx) {
        ctx.ensureSpace(14f);
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.rgb(230, 230, 230));
        linePaint.setStrokeWidth(0.75f);
        ctx.canvas.drawLine(MARGIN_HORIZONTAL, ctx.currentY + 4f,
                PAGE_WIDTH - MARGIN_HORIZONTAL, ctx.currentY + 4f, linePaint);
        ctx.currentY += 12f;
    }

    /**
     * Create StaticLayout compatible with all supported Android versions
     */
    private static StaticLayout createStaticLayout(CharSequence text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build();
    }

    /**
     * State container managing active page and pagination for PdfDocument
     */
    private static class PdfContext {
        final PdfDocument document;
        PdfDocument.Page currentPage;
        Canvas canvas;
        int pageNumber;
        float currentY;
        final float maxY = PAGE_HEIGHT - MARGIN_BOTTOM;

        PdfContext(PdfDocument doc) {
            this.document = doc;
            this.pageNumber = 1;
            startNewPage();
        }

        private void startNewPage() {
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
            currentPage = document.startPage(pageInfo);
            canvas = currentPage.getCanvas();
            currentY = MARGIN_TOP;
        }

        void newPage() {
            drawFooter();
            document.finishPage(currentPage);
            pageNumber++;
            startNewPage();
        }

        void ensureSpace(float neededHeight) {
            if (currentY + neededHeight > maxY) {
                newPage();
            }
        }

        void drawFooter() {
            Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            footerPaint.setColor(Color.rgb(150, 150, 150));
            footerPaint.setTextSize(8.5f);
            footerPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Exported from MobiGPT  •  Page " + pageNumber,
                    PAGE_WIDTH / 2f, PAGE_HEIGHT - 22f, footerPaint);
        }

        void finish() {
            if (currentPage != null) {
                drawFooter();
                document.finishPage(currentPage);
                currentPage = null;
            }
        }
    }

    /**
     * Share a file using Android's share intent
     */
    public static void shareFile(Context context, Uri fileUri, String mimeType) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "MobiGPT Conversation Export");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(shareIntent, "Share conversation via"));
            Log.d(TAG, "Sharing file via intent");

        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
        }
    }

    /**
     * Get MIME type for a file
     */
    public static String getMimeType(String filename) {
        if (filename.endsWith(".md")) {
            return "text/markdown";
        } else if (filename.endsWith(".pdf")) {
            return "application/pdf";
        } else {
            return "text/plain";
        }
    }

    /**
     * Sanitize filename by removing invalid characters
     */
    private static String sanitizeFilename(String filename) {
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }

    /**
     * Show notification when export is complete
     */
    private static void showExportNotification(Context context, String filename, Uri fileUri, String mimeType) {
        NotificationChannel channel = new NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Export Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Notifications for exported conversations");

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(fileUri, mimeType);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Export Complete")
            .setContentText("Saved to Downloads: " + filename)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Saved to Downloads: " + filename + "\n\nTap to open"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }
}
