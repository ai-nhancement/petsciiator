package com.sixtyfour.petscii.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

/**
 * Shows an image scaled to fit, keeping its display aspect ratio.
 */
public class ImagePanel extends JComponent {

	private static final long serialVersionUID = 1L;

	private BufferedImage image;
	private int displayWidth = 320;
	private int displayHeight = 200;
	private final boolean pixelated;
	private String placeholder;

	public ImagePanel(boolean pixelated, String placeholder) {
		this.pixelated = pixelated;
		this.placeholder = placeholder;
		setPreferredSize(new Dimension(640, 400));
		setMinimumSize(new Dimension(160, 100));
	}

	/**
	 * Sets the image. The display size defines the aspect ratio in which it will
	 * be shown, which allows to show Koala's wide pixels correctly.
	 */
	public void setImage(BufferedImage image, int displayWidth, int displayHeight) {
		this.image = image;
		this.displayWidth = displayWidth;
		this.displayHeight = displayHeight;
		repaint();
	}

	public void setImage(BufferedImage image) {
		setImage(image, image == null ? 320 : image.getWidth(), image == null ? 200 : image.getHeight());
	}

	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		int w = getWidth();
		int h = getHeight();
		g2.setColor(new Color(0x2b2b2b));
		g2.fillRect(0, 0, w, h);

		if (image == null) {
			if (placeholder != null) {
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(new Color(0x9a9a9a));
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(placeholder, (w - fm.stringWidth(placeholder)) / 2, (h + fm.getAscent()) / 2);
			}
			g2.dispose();
			return;
		}

		double scale = Math.min((double) w / displayWidth, (double) h / displayHeight);
		if (pixelated && scale >= 2) {
			// Integer scaling keeps the characters crisp, but below 2x it would
			// waste too much space
			scale = Math.floor(scale);
		}
		int dw = (int) (displayWidth * scale);
		int dh = (int) (displayHeight * scale);
		int x = (w - dw) / 2;
		int y = (h - dh) / 2;

		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				pixelated ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
						: RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(image, x, y, dw, dh, null);
		g2.dispose();
	}
}
