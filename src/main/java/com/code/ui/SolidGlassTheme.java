package com.code.ui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Solid Glass 视觉设计系统（三层灰阶架构 + 收敛强调色 + 高对比度可读性规范）
 *
 * 严格遵循三层背景与语义配色：
 * - 页面底色：#141619 | 面板背景：#1C1F24 | 输入与编辑背景：#171A1F | 悬停背景：#242830
 * - 容器边框与分隔线：#343A46 | 焦点边框：#6AA8FF | 主按钮：#356FE0
 * - 主要文字：#E6E8ED (13.48:1) | 次要文字：#A8B0BD | 辅助文字：#909AA9 (6.13:1)
 * - 选中背景/文字：#263A57 / #82B5FF
 * - 成功：#66C18C / #1D3529 | 警告：#E4B567 / #3A3020 | 错误：#F08B8B / #402629
 *
 * @author syrobin
 */
public final class SolidGlassTheme {

    private SolidGlassTheme() {
    }

    public enum Thickness {
        THIN(6),
        MEDIUM(8),
        THICK(8);

        final int radius;

        Thickness(int radius) {
            this.radius = radius;
        }
    }

    /**
     * 连接/解析验证状态枚举（绿色仅用于已确认的成功状态）
     */
    public enum GlyphState {
        IDLE,       // 未验证 / 待命（中性灰）
        WORKING,    // 正在验证 / 加载中（交互蓝）
        READY,      // 已确认成功（成功绿 #66C18C / #1D3529）
        WARNING,    // 警告 / 需注意（警告黄 #E4B567 / #3A3020）
        ERROR       // 失败 / 冲突（错误红 #F08B8B / #402629）
    }

    // ==================== 核心色彩令牌（映射宿主明暗主题） ====================

    public static boolean isDark() {
        return !JBColor.isBright();
    }

    /** 页面底色：#141619 */
    public static Color canvasBg() {
        return isDark() ? new Color(0x14, 0x16, 0x19) : new Color(0xF3, 0xF4, 0xF6);
    }

    /** 面板背景：#1C1F24 */
    public static Color panelBg() {
        return isDark() ? new Color(0x1C, 0x1F, 0x24) : new Color(0xFF, 0xFF, 0xFF);
    }

    public static Color surfaceBg(Thickness ignored) {
        return panelBg();
    }

    /** 输入与编辑背景：#171A1F */
    public static Color inputBg() {
        return isDark() ? new Color(0x17, 0x1A, 0x1F) : new Color(0xF8, 0xFA, 0xFC);
    }

    /** 悬停背景：#242830 */
    public static Color hoverBg() {
        return isDark() ? new Color(0x24, 0x28, 0x30) : new Color(0xEE, 0xF2, 0xF6);
    }

    /** 容器边框与分隔线：#343A46 */
    public static Color borderColor() {
        return isDark() ? new Color(0x34, 0x3A, 0x46) : new Color(0xD1, 0xD5, 0xDB);
    }

    public static Color rimOuterBorder() {
        return borderColor();
    }

    /** 主要文字：#E6E8ED */
    public static Color textPrimary() {
        return isDark() ? new Color(0xE6, 0xE8, 0xED) : new Color(0x11, 0x18, 0x27);
    }

    /** 次要文字：#A8B0BD */
    public static Color textSecondary() {
        return isDark() ? new Color(0xA8, 0xB0, 0xBD) : new Color(0x4B, 0x55, 0x63);
    }

    /** 辅助文字：#909AA9（提亮有效说明，保证 6.13:1 对比度） */
    public static Color textMuted() {
        return isDark() ? new Color(0x90, 0x9A, 0xA9) : new Color(0x64, 0x74, 0x8B);
    }

    /** 主按钮背景：#356FE0 */
    public static Color primaryButtonBg() {
        return isDark() ? new Color(0x35, 0x6F, 0xE0) : new Color(0x25, 0x63, 0xEB);
    }

    /** 焦点边框：#6AA8FF */
    public static Color focusBorderColor() {
        return isDark() ? new Color(0x6A, 0xA8, 0xFF) : new Color(0x3B, 0x82, 0xF6);
    }

    /** 选中背景：#263A57 */
    public static Color selectedBg() {
        return isDark() ? new Color(0x26, 0x3A, 0x57) : new Color(0xDB, 0xEA, 0xFE);
    }

    /** 选中文字 / 交互蓝：#82B5FF */
    public static Color selectedFg() {
        return isDark() ? new Color(0x82, 0xB5, 0xFF) : new Color(0x1D, 0x4E, 0xD8);
    }

    public static Color accentColor() {
        return selectedFg();
    }

    /** 成功文字：#66C18C | 成功背景：#1D3529 */
    public static Color successFg() {
        return isDark() ? new Color(0x66, 0xC1, 0x8C) : new Color(0x15, 0x80, 0x3D);
    }

    public static Color successBg() {
        return isDark() ? new Color(0x1D, 0x35, 0x29) : new Color(0xDC, 0xFC, 0xE7);
    }

    /** 警告文字：#E4B567 | 警告背景：#3A3020 */
    public static Color warningFg() {
        return isDark() ? new Color(0xE4, 0xB5, 0x67) : new Color(0xB4, 0x53, 0x09);
    }

    public static Color warningBg() {
        return isDark() ? new Color(0x3A, 0x30, 0x20) : new Color(0xFE, 0xF3, 0xC7);
    }

    /** 错误文字：#F08B8B | 错误背景：#402629 */
    public static Color errorFg() {
        return isDark() ? new Color(0xF0, 0x8B, 0x8B) : new Color(0xB9, 0x1C, 0x1C);
    }

    public static Color errorBg() {
        return isDark() ? new Color(0x40, 0x26, 0x29) : new Color(0xFE, 0xE2, 0xE2);
    }

    /**
     * 创建高可读性的辅助说明标签（使用 #909AA9 辅助文字色，避免接近禁用态的暗灰）
     */
    public static JLabel createHelperLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f)));
        label.setForeground(textMuted());
        return label;
    }

    /**
     * 将输入框或滚动容器統一设置为 #171A1F 编辑区背景与 #343A46 1px 细边框，消除多层套框白边
     */
    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBackground(inputBg());
        scrollPane.getViewport().setBackground(inputBg());
        scrollPane.setBorder(BorderFactory.createLineBorder(borderColor(), 1));
    }

    /**
     * 创建消除亮白分隔条的定制 JSplitPane：
     * - 默认轨道使用 #343A46，中央抓手略亮
     * - 鼠标悬停或拥有焦点时切换为 #6AA8FF
     */
    public static JSplitPane createCleanSplitPane(int orientation, Component left, Component right, int dividerLocation, double resizeWeight) {
        JSplitPane splitPane = new JSplitPane(orientation, left, right);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        splitPane.setDividerSize(JBUI.scale(7));
        splitPane.setDividerLocation(dividerLocation);
        splitPane.setResizeWeight(resizeWeight);

        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new CleanSplitPaneDivider(this);
            }
        });
        return splitPane;
    }

    private static class CleanSplitPaneDivider extends BasicSplitPaneDivider {
        private boolean hovered = false;
        private boolean focused = false;

        public CleanSplitPaneDivider(BasicSplitPaneUI ui) {
            super(ui);
            setBorder(null);
            setBackground(canvasBg());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(panelBg());
                g2.fillRect(0, 0, w, h);

                Color lineCol = (hovered || focused) ? focusBorderColor() : borderColor();
                Color handleCol = (hovered || focused)
                        ? focusBorderColor()
                        : (isDark() ? new Color(0x52, 0x5B, 0x6C) : new Color(0x9C, 0xA3, 0xAF));

                if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    int cx = w / 2;
                    g2.setColor(lineCol);
                    g2.drawLine(cx, 2, cx, h - 3);
                    g2.setColor(handleCol);
                    g2.fillRoundRect(cx - 1, Math.max(4, h / 2 - 14), 3, Math.min(28, h - 8), 2, 2);
                } else {
                    int cy = h / 2;
                    g2.setColor(lineCol);
                    g2.drawLine(2, cy, w - 3, cy);
                    g2.setColor(handleCol);
                    g2.fillRoundRect(Math.max(4, w / 2 - 14), cy - 1, Math.min(28, w - 8), 3, 2, 2);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // ==================== 核心组件 1：SolidGlassPanel（#1C1F24 面板层，去厚阴影与多余套框） ====================

    public static class SolidGlassPanel extends JPanel {
        private final Thickness thickness;

        public SolidGlassPanel(LayoutManager layout, Thickness thickness) {
            super(layout);
            this.thickness = thickness;
            setOpaque(false);
            setBorder(JBUI.Borders.empty(8, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int r = JBUI.scale(thickness.radius);

                g2.setColor(panelBg());
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, r, r));

                g2.setColor(borderColor());
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, r, r));
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // ==================== 核心组件 2：SourceBadge（中性来源标签，与验证状态解耦） ====================

    public static class SourceBadge extends JComponent {
        private String sourceText;

        public SourceBadge(String initialSourceText) {
            this.sourceText = initialSourceText;
            setOpaque(false);
        }

        public void setSourceText(String sourceText) {
            this.sourceText = sourceText;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Font font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f));
            FontMetrics fm = getFontMetrics(font);
            int w = fm.stringWidth(StringUtils.defaultString(sourceText)) + 26;
            return new Dimension(w, 24);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                g2.setColor(inputBg());
                g2.fillRoundRect(0, 1, w - 1, h - 2, 6, 6);
                g2.setColor(borderColor());
                g2.drawRoundRect(0, 1, w - 2, h - 3, 6, 6);

                // 中性几何方形图标（代表数据来源，绝不使用绿点混淆连接状态）
                g2.setColor(textSecondary());
                g2.drawRoundRect(8, h / 2 - 4, 8, 8, 2, 2);
                g2.drawLine(8, h / 2, 16, h / 2);

                Font font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f));
                g2.setFont(font);
                g2.setColor(textPrimary());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(StringUtils.defaultString(sourceText), 21, (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    // ==================== 核心组件 3：StatusGlyph（独立验证状态徽标：绿色仅用于已确认成功状态） ====================

    public static class StatusGlyph extends JComponent {
        private GlyphState state = GlyphState.IDLE;
        private String statusText = "未验证";

        public StatusGlyph(String initialText, GlyphState initialState) {
            this.statusText = initialText;
            this.state = initialState;
            setOpaque(false);
            setBorder(new EmptyBorder(2, 8, 2, 8));
        }

        public void setStatus(String text, GlyphState newState) {
            this.statusText = text;
            this.state = newState;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Font font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f));
            FontMetrics fm = getFontMetrics(font);
            int textW = StringUtils.isNotBlank(statusText) ? fm.stringWidth(statusText) : 0;
            return new Dimension(24 + textW + 10, 24);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                Color bg;
                Color fg;
                Color border;
                switch (state) {
                    case READY:
                        bg = successBg();
                        fg = successFg();
                        border = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 90);
                        break;
                    case WORKING:
                        bg = selectedBg();
                        fg = selectedFg();
                        border = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 90);
                        break;
                    case WARNING:
                        bg = warningBg();
                        fg = warningFg();
                        border = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 90);
                        break;
                    case ERROR:
                        bg = errorBg();
                        fg = errorFg();
                        border = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 90);
                        break;
                    case IDLE:
                    default:
                        bg = inputBg();
                        fg = textSecondary();
                        border = borderColor();
                        break;
                }

                g2.setColor(bg);
                g2.fillRoundRect(0, 1, w - 1, h - 2, 6, 6);
                g2.setColor(border);
                g2.drawRoundRect(0, 1, w - 2, h - 3, 6, 6);

                // 单一语义状态点（实心圆=已确认成功/错误，空心环=未验证/待命）
                int dotX = 9;
                int dotY = h / 2 - 3;
                g2.setColor(fg);
                if (state == GlyphState.IDLE) {
                    g2.drawOval(dotX, dotY, 6, 6);
                } else {
                    g2.fillOval(dotX, dotY, 6, 6);
                }

                if (StringUtils.isNotBlank(statusText)) {
                    Font font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f));
                    g2.setFont(font);
                    g2.setColor(fg);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(statusText, dotX + 11, (h - fm.getHeight()) / 2 + fm.getAscent());
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // ==================== 核心组件 4：GlassButton（主操作 #356FE0 配白字，次级按钮悬停 #242830） ====================

    public static class GlassButton extends JButton {
        private final boolean emphasized;
        private boolean hovered = false;
        private boolean pressed = false;

        public GlassButton(String text) {
            this(text, false);
        }

        public GlassButton(String text, boolean emphasized) {
            super(text);
            this.emphasized = emphasized;
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(4, 11, 5, 11));
            setFont(UIUtil.getLabelFont().deriveFont(emphasized ? Font.BOLD : Font.PLAIN, JBUI.scaleFontSize(11.5f)));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int r = JBUI.scale(6);
                int yOffset = (pressed && isEnabled()) ? 1 : 0;

                if (!isEnabled()) {
                    g2.setColor(inputBg());
                    g2.fillRoundRect(0, 0, w - 1, h - 2, r, r);
                    g2.setColor(borderColor());
                    g2.drawRoundRect(0, 0, w - 2, h - 3, r, r);
                    g2.setFont(getFont());
                    g2.setColor(textMuted());
                } else if (emphasized) {
                    Color base = primaryButtonBg();
                    Color bg = hovered
                            ? new Color(Math.min(255, base.getRed() + 18), Math.min(255, base.getGreen() + 18), Math.min(255, base.getBlue() + 18))
                            : base;
                    g2.setColor(bg);
                    g2.fillRoundRect(0, yOffset, w - 1, h - 2, r, r);
                    if (isFocusOwner()) {
                        g2.setColor(focusBorderColor());
                        g2.drawRoundRect(0, yOffset, w - 2, h - 3, r, r);
                    }
                    g2.setFont(getFont());
                    g2.setColor(Color.WHITE);
                } else {
                    Color bg = hovered ? hoverBg() : inputBg();
                    g2.setColor(bg);
                    g2.fillRoundRect(0, yOffset, w - 1, h - 2, r, r);
                    g2.setColor(isFocusOwner() ? focusBorderColor() : borderColor());
                    g2.drawRoundRect(0, yOffset, w - 2, h - 3, r, r);
                    g2.setFont(getFont());
                    g2.setColor(textPrimary());
                }

                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h - 2 - fm.getHeight()) / 2 + fm.getAscent() + yOffset;
                g2.drawString(getText(), tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

    // ==================== 核心组件 5：SqlSyntaxHighlightPane（克制的 DDL SQL 语法高亮编辑器） ====================

    /**
     * 克制的 SQL 语法高亮组件：
     * - 关键词：#9EBBFF | 字符串：#B3C992 | 标识符：主文字色 #E6E8ED | 注释：辅助文字色 #909AA9
     */
    public static class SqlSyntaxHighlightPane extends JTextPane {
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "(?<COMMENT>--[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
                "|(?<STRING>'(?:\\\\.|[^'\\\\])*')" +
                "|(?<IDENT>`[^`]+`)" +
                "|(?<KEYWORD>\\b(?i:CREATE|TABLE|IF|NOT|EXISTS|PRIMARY|KEY|UNIQUE|INDEX|DEFAULT|NULL|COMMENT|ENGINE|CHARSET|COLLATE|AUTO_INCREMENT|BIGINT|INT|INTEGER|TINYINT|SMALLINT|DECIMAL|NUMERIC|DOUBLE|FLOAT|VARCHAR|CHAR|TEXT|LONGTEXT|DATETIME|DATE|TIMESTAMP|BOOLEAN|BIT|BLOB|JSON|UNSIGNED|CONSTRAINT|FOREIGN|REFERENCES)\\b)"
        );

        private boolean highlighting = false;

        public SqlSyntaxHighlightPane() {
            setOpaque(true);
            setBackground(inputBg());
            setForeground(textPrimary());
            setCaretColor(textPrimary());
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(13f)));
            setMargin(JBUI.insets(8, 10));

            getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    scheduleHighlight();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    scheduleHighlight();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                }
            });
        }

        private void scheduleHighlight() {
            if (highlighting) {
                return;
            }
            SwingUtilities.invokeLater(this::applySyntaxHighlighting);
        }

        private void applySyntaxHighlighting() {
            if (highlighting) {
                return;
            }
            highlighting = true;
            try {
                StyledDocument doc = getStyledDocument();
                String text = doc.getText(0, doc.getLength());
                if (text.isEmpty()) {
                    return;
                }

                SimpleAttributeSet normalAttr = new SimpleAttributeSet();
                StyleConstants.setForeground(normalAttr, textPrimary());

                SimpleAttributeSet keywordAttr = new SimpleAttributeSet();
                StyleConstants.setForeground(keywordAttr, isDark() ? new Color(0x9E, 0xBB, 0xFF) : new Color(0x25, 0x63, 0xEB));
                StyleConstants.setBold(keywordAttr, true);

                SimpleAttributeSet stringAttr = new SimpleAttributeSet();
                StyleConstants.setForeground(stringAttr, isDark() ? new Color(0xB3, 0xC9, 0x92) : new Color(0x15, 0x80, 0x3D));

                SimpleAttributeSet identAttr = new SimpleAttributeSet();
                StyleConstants.setForeground(identAttr, textPrimary());

                SimpleAttributeSet commentAttr = new SimpleAttributeSet();
                StyleConstants.setForeground(commentAttr, textMuted());
                StyleConstants.setItalic(commentAttr, true);

                doc.setCharacterAttributes(0, text.length(), normalAttr, true);

                Matcher matcher = TOKEN_PATTERN.matcher(text);
                while (matcher.find()) {
                    int start = matcher.start();
                    int len = matcher.end() - start;
                    if (matcher.group("COMMENT") != null) {
                        doc.setCharacterAttributes(start, len, commentAttr, true);
                    } else if (matcher.group("STRING") != null) {
                        doc.setCharacterAttributes(start, len, stringAttr, true);
                    } else if (matcher.group("IDENT") != null) {
                        doc.setCharacterAttributes(start, len, identAttr, true);
                    } else if (matcher.group("KEYWORD") != null) {
                        doc.setCharacterAttributes(start, len, keywordAttr, true);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                highlighting = false;
            }
        }
    }

    /**
     * 构建紧凑单行顶栏
     */
    public static JPanel createCompactHeader(String title, String versionText, JComponent rightStatusWidget) {
        SolidGlassPanel header = new SolidGlassPanel(new BorderLayout(10, 0), Thickness.MEDIUM);
        header.setBorder(JBUI.Borders.empty(6, 12));

        JPanel leftRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftRow.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(13.5f)));
        titleLabel.setForeground(textPrimary());
        leftRow.add(titleLabel);

        if (StringUtils.isNotBlank(versionText)) {
            JLabel verLabel = new JLabel(versionText);
            verLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11f)));
            verLabel.setForeground(textMuted());
            leftRow.add(verLabel);
        }

        header.add(leftRow, BorderLayout.WEST);
        if (rightStatusWidget != null) {
            header.add(rightStatusWidget, BorderLayout.EAST);
        }
        return header;
    }
}
