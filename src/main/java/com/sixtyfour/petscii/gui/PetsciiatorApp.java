package com.sixtyfour.petscii.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.sixtyfour.petscii.Algorithm;
import com.sixtyfour.petscii.TedColors;
import com.sixtyfour.petscii.Vic2Colors;

/**
 * Desktop version of the converter: load an image, tweak the settings while
 * watching the result and export it into the formats of the command line
 * version.
 */
public class PetsciiatorApp extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final String TITLE = "PETSCIIator";

	private static final String[] FORMATS = { "image", "basic", "bbs", "bin", "koala", "hires" };
	private static final String[] FORMAT_LABELS = { "PNG image", "BASIC program", "BBS (SEQ)", "Screen/color RAM (SEQ)",
			"Koala Painter", "Hi-Eddi hires" };

	private enum Mode {
		PETSCII, KOALA, HIRES
	}

	private final Preferences prefs = Preferences.userNodeForPackage(PetsciiatorApp.class);
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "converter");
		t.setDaemon(true);
		return t;
	});

	private File sourceFile;
	private Mode mode = Mode.PETSCII;
	private int generation = 0;
	private Converter.PetsciiResult lastPetscii;
	private final Timer refreshTimer;

	private final ImagePanel sourcePanel = new ImagePanel(false, "Drop an image (or a folder for batch), paste one (Ctrl+V) or click Open");
	private final ImagePanel resultPanel = new ImagePanel(true, "");
	private final JLabel status = new JLabel(" ");

	private final JComboBox<String> platform = new JComboBox<>(new String[] { "Commodore 64", "C16 / C116 / Plus/4" });
	private final JComboBox<String> colorMapper = new JComboBox<>(new String[] { "Colorful", "Soft", "Dithered" });
	private final JSpinner colorMode = new JSpinner(new SpinnerNumberModel(1, 0, 2, 1));
	private final JCheckBox autoBackground = new JCheckBox("Auto detect", true);
	private final JSpinner background = new JSpinner(new SpinnerNumberModel(0, 0, 15, 1));
	private final JLabel backgroundSwatch = new JLabel();
	private final JSlider prescale = new JSlider(1, 4, 1);
	private final JCheckBox lowerCase = new JCheckBox("Use lower case character set");
	private final JCheckBox noAlpha = new JCheckBox("No letters and digits");
	private final JSpinner symbolBoost = new JSpinner(new SpinnerNumberModel(10, -100, 100, 1));
	private final JTextField excludes = new JTextField();
	private final JSlider dither = new JSlider(0, 100, 100);
	private final Map<String, JCheckBox> formatBoxes = new LinkedHashMap<>();

	private final JToggleButton petsciiButton = new JToggleButton("PETSCII", true);
	private final JToggleButton koalaButton = new JToggleButton("Koala");
	private final JToggleButton hiresButton = new JToggleButton("Hires");
	private final JButton exportButton = new JButton("Export...");
	private final JButton copyBasicButton = new JButton("Copy BASIC");

	public static void main(String[] args) {
		System.setProperty("sun.java2d.uiScale.enabled", "true");
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				// Keep the default look and feel then
			}
			PetsciiatorApp app = new PetsciiatorApp();
			app.setVisible(true);
			if (args.length == 1 && !new File(args[0]).isDirectory()) {
				app.open(new File(args[0]));
			} else if (args.length > 0) {
				List<File> files = new ArrayList<>();
				for (String arg : args) {
					files.add(new File(arg));
				}
				app.openBatch(files);
			}
		});
	}

	public PetsciiatorApp() {
		super(TITLE);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setIconImages(createIcons());

		refreshTimer = new Timer(200, e -> refresh());
		refreshTimer.setRepeats(false);

		JSplitPane previews = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, titled(sourcePanel, "Source"),
				titled(resultPanel, "Result"));
		previews.setResizeWeight(0.5);
		previews.setBorder(null);

		JScrollPane settingsScroller = new JScrollPane(createSettingsPanel());
		settingsScroller.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xcccccc)));
		settingsScroller.getVerticalScrollBar().setUnitIncrement(16);
		settingsScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		JPanel content = new JPanel(new BorderLayout());
		content.add(createToolbar(), BorderLayout.NORTH);
		content.add(previews, BorderLayout.CENTER);
		content.add(settingsScroller, BorderLayout.EAST);
		content.add(status, BorderLayout.SOUTH);
		setContentPane(content);

		installDropAndPaste(content);
		updateEnabledStates();

		setSize(1280, 760);
		setMinimumSize(new Dimension(900, 520));
		setLocationRelativeTo(null);
	}

	private JComponent createToolbar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		JButton openButton = new JButton("Open...");
		openButton.addActionListener(e -> chooseFile());
		left.add(openButton);
		exportButton.addActionListener(e -> export());
		left.add(exportButton);
		copyBasicButton.setToolTipText("Copy the BASIC program to the clipboard");
		copyBasicButton.addActionListener(e -> copyBasic());
		left.add(copyBasicButton);
		JButton batchButton = new JButton("Batch...");
		batchButton.setToolTipText("Convert many images at once with the current settings");
		batchButton.addActionListener(e -> openBatch(null));
		left.add(batchButton);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		right.add(new JLabel("Preview:  "));
		ButtonGroup group = new ButtonGroup();
		for (JToggleButton button : new JToggleButton[] { petsciiButton, koalaButton, hiresButton }) {
			group.add(button);
			right.add(button);
		}
		petsciiButton.addActionListener(e -> setMode(Mode.PETSCII));
		koalaButton.addActionListener(e -> setMode(Mode.KOALA));
		hiresButton.addActionListener(e -> setMode(Mode.HIRES));
		petsciiButton.setToolTipText("Character based PETSCII image");
		koalaButton.setToolTipText("Multicolor bitmap (not PETSCII)");
		hiresButton.setToolTipText("Hires bitmap (not PETSCII)");

		bar.add(left, BorderLayout.WEST);
		bar.add(right, BorderLayout.EAST);
		return bar;
	}

	private JComponent createSettingsPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
		Row row = new Row(panel);

		row.heading("PETSCII");
		row.add("Platform", platform, "The C16/Plus/4 offers more colors");
		row.add("Color mapper", colorMapper, "How the image's colors are mapped to the machine's palette");
		row.add("Color mode", colorMode, "Only used by the soft color mapper");

		JPanel bgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		backgroundSwatch.setOpaque(true);
		backgroundSwatch.setPreferredSize(new Dimension(18, 18));
		backgroundSwatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
		bgPanel.add(autoBackground);
		bgPanel.add(background);
		bgPanel.add(backgroundSwatch);
		row.add("Background", bgPanel,
				"Overriding it can bring out more detail in areas of that color");

		prescale.setMajorTickSpacing(1);
		prescale.setPaintTicks(true);
		prescale.setPaintLabels(true);
		prescale.setSnapToTicks(true);
		row.add("Prescale", prescale, "Scales the image down first, can reduce artifacts. 4 is about 80x50");
		row.add("Symbol boost", symbolBoost,
				"Above 10 favours graphic symbols, below favours other chars. Negative inverts");
		row.add("Exclude chars", excludes, "Comma separated PETSCII char codes, e.g. 32,96");
		row.add("", lowerCase, null);
		row.add("", noAlpha, null);

		row.heading("Koala / Hires");
		dither.setMajorTickSpacing(25);
		dither.setPaintTicks(true);
		dither.setPaintLabels(true);
		row.add("Dithering", dither, "Doesn't affect the PETSCII conversion");

		row.heading("Export formats");
		for (int i = 0; i < FORMATS.length; i++) {
			JCheckBox box = new JCheckBox(FORMAT_LABELS[i]);
			formatBoxes.put(FORMATS[i], box);
			row.add("", box, null);
		}
		formatBoxes.get("image").setSelected(prefs.getBoolean("format.image", true));
		formatBoxes.get("basic").setSelected(prefs.getBoolean("format.basic", true));
		for (String format : new String[] { "bbs", "bin", "koala", "hires" }) {
			formatBoxes.get(format).setSelected(prefs.getBoolean("format." + format, false));
		}
		formatBoxes.get("bbs").setToolTipText("Also removes control codes from the conversion");
		JLabel bbsHint = new JLabel("BBS output removes control codes from the PETSCII result");
		bbsHint.setForeground(Color.GRAY);
		row.add("", bbsHint, null);

		row.finish();

		// Every change triggers a new conversion
		platform.addActionListener(e -> {
			updateBackgroundRange();
			settingsChanged();
		});
		colorMapper.addActionListener(e -> settingsChanged());
		colorMode.addChangeListener(e -> settingsChanged());
		autoBackground.addActionListener(e -> settingsChanged());
		background.addChangeListener(e -> settingsChanged());
		prescale.addChangeListener(e -> {
			if (!prescale.getValueIsAdjusting()) {
				settingsChanged();
			}
		});
		symbolBoost.addChangeListener(e -> settingsChanged());
		lowerCase.addActionListener(e -> settingsChanged());
		noAlpha.addActionListener(e -> settingsChanged());
		dither.addChangeListener(e -> {
			if (!dither.getValueIsAdjusting()) {
				settingsChanged();
			}
		});
		excludes.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				settingsChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				settingsChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				settingsChanged();
			}
		});
		for (Map.Entry<String, JCheckBox> entry : formatBoxes.entrySet()) {
			entry.getValue().addActionListener(e -> {
				prefs.putBoolean("format." + entry.getKey(), entry.getValue().isSelected());
				if (entry.getKey().equals("bbs")) {
					settingsChanged();
				}
			});
		}

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(panel, BorderLayout.NORTH);
		wrapper.setPreferredSize(new Dimension(340, panel.getPreferredSize().height));
		return wrapper;
	}

	private void settingsChanged() {
		updateEnabledStates();
		if (sourceFile != null) {
			refreshTimer.restart();
		}
	}

	private void updateEnabledStates() {
		boolean petscii = mode == Mode.PETSCII;
		boolean c64 = platform.getSelectedIndex() == 0;
		platform.setEnabled(petscii);
		colorMapper.setEnabled(petscii);
		colorMode.setEnabled(petscii && colorMapper.getSelectedIndex() == 1);
		autoBackground.setEnabled(petscii);
		background.setEnabled(petscii && !autoBackground.isSelected());
		prescale.setEnabled(petscii);
		symbolBoost.setEnabled(petscii);
		excludes.setEnabled(petscii);
		lowerCase.setEnabled(petscii);
		noAlpha.setEnabled(petscii);
		dither.setEnabled(!petscii);
		exportButton.setEnabled(sourceFile != null);
		copyBasicButton.setEnabled(lastPetscii != null && petscii);

		int idx = (Integer) background.getValue();
		int[] colors = c64 ? new Vic2Colors().getColors() : new TedColors().getColors();
		backgroundSwatch.setBackground(new Color(colors[Math.min(idx, colors.length - 1)]));
		backgroundSwatch.setVisible(!autoBackground.isSelected());
	}

	private void updateBackgroundRange() {
		int max = platform.getSelectedIndex() == 0 ? 15 : 127;
		SpinnerNumberModel model = (SpinnerNumberModel) background.getModel();
		model.setMaximum(max);
		if ((Integer) model.getValue() > max) {
			model.setValue(max);
		}
	}

	private ConversionSettings readSettings() {
		ConversionSettings s = new ConversionSettings();
		s.tedMode = platform.getSelectedIndex() == 1;
		s.algorithm = new Algorithm[] { Algorithm.COLORFUL, Algorithm.SOFT, Algorithm.DITHERED }[colorMapper
				.getSelectedIndex()];
		s.colorMode = (Integer) colorMode.getValue();
		s.background = autoBackground.isSelected() ? null : (Integer) background.getValue();
		s.prescale = prescale.getValue();
		s.lowerCase = lowerCase.isSelected();
		s.noAlpha = noAlpha.isSelected();
		s.symbolBoost = (Integer) symbolBoost.getValue();
		s.excludes = ConversionSettings.parseExcludes(excludes.getText());
		s.dither = dither.getValue();
		for (Map.Entry<String, JCheckBox> entry : formatBoxes.entrySet()) {
			if (entry.getValue().isSelected()) {
				s.formats.add(entry.getKey());
			}
		}
		return s;
	}

	private void setMode(Mode mode) {
		this.mode = mode;
		settingsChanged();
	}

	private void open(File file) {
		BufferedImage img;
		try {
			img = ImageIO.read(file);
		} catch (IOException e) {
			img = null;
		}
		if (img == null) {
			JOptionPane.showMessageDialog(this, "Can't read " + file.getName() + " as an image.", TITLE,
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		sourceFile = file;
		lastPetscii = null;
		if (file.getParentFile() != null && !isTempFile(file)) {
			prefs.put("openDir", file.getParentFile().getPath());
		}
		setTitle(file.getName() + " - " + TITLE);
		sourcePanel.setImage(img);
		resultPanel.setImage(null);
		updateEnabledStates();
		refresh();
	}

	private void refresh() {
		if (sourceFile == null) {
			return;
		}
		refreshTimer.stop();
		final ConversionSettings settings;
		try {
			settings = readSettings();
			excludes.setForeground(UIManager.getColor("TextField.foreground"));
		} catch (IllegalArgumentException e) {
			excludes.setForeground(Color.RED);
			status.setText("Excluded chars: " + e.getMessage());
			return;
		}

		final int gen = ++generation;
		final File file = sourceFile;
		final Mode currentMode = mode;
		resultPanel.setPlaceholder("Converting...");
		status.setText("Converting...");

		worker.submit(() -> {
			long start = System.currentTimeMillis();
			try {
				Converter.PetsciiResult petscii = null;
				BufferedImage image;
				if (currentMode == Mode.PETSCII) {
					petscii = Converter.convertToPetscii(file, settings);
					image = petscii.bitmap.getImage();
				} else if (currentMode == Mode.KOALA) {
					image = Converter.createKoalaPreview(file, settings);
				} else {
					image = Converter.createHiresPreview(file, settings);
				}
				long time = System.currentTimeMillis() - start;
				final Converter.PetsciiResult petsciiResult = petscii;
				SwingUtilities.invokeLater(() -> {
					if (gen != generation) {
						return;
					}
					lastPetscii = petsciiResult;
					// Koala pixels are twice as wide as they are high
					resultPanel.setImage(image, 320, 200);
					String text = "Converted in " + time + " ms";
					if (petsciiResult != null) {
						text += "  ·  Background color: " + petsciiResult.data.getBackGroundColor();
					}
					status.setText(text);
					updateEnabledStates();
				});
			} catch (Throwable t) {
				SwingUtilities.invokeLater(() -> {
					if (gen != generation) {
						return;
					}
					lastPetscii = null;
					resultPanel.setImage(null);
					resultPanel.setPlaceholder("Conversion failed");
					status.setText("Conversion failed: " + t);
					updateEnabledStates();
				});
			}
		});
	}

	private void chooseFile() {
		JFileChooser chooser = new JFileChooser(prefs.get("openDir", null));
		chooser.setDialogTitle("Open image");
		chooser.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG, GIF, BMP, WebP)", BatchDialog.EXTENSIONS));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			open(chooser.getSelectedFile());
		}
	}

	private void export() {
		if (sourceFile == null) {
			return;
		}
		final ConversionSettings settings;
		try {
			settings = readSettings();
		} catch (IllegalArgumentException e) {
			JOptionPane.showMessageDialog(this, "Excluded chars: " + e.getMessage(), TITLE,
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (settings.formats.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Select at least one export format on the right.", TITLE,
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		String startDir = prefs.get("exportDir", null);
		if (startDir == null && !isTempFile(sourceFile)) {
			startDir = sourceFile.getParent();
		}
		JFileChooser chooser = new JFileChooser(startDir);
		chooser.setDialogTitle("Export to folder (existing files with the same names are replaced)");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showDialog(this, "Export here") != JFileChooser.APPROVE_OPTION) {
			return;
		}
		final File folder = chooser.getSelectedFile();
		folder.mkdirs();
		prefs.put("exportDir", folder.getPath());

		final File file = sourceFile;
		exportButton.setEnabled(false);
		status.setText("Exporting...");
		worker.submit(() -> {
			try {
				List<String> written = Converter.export(file, settings, folder);
				SwingUtilities.invokeLater(() -> exportDone(folder, written));
			} catch (Throwable t) {
				SwingUtilities.invokeLater(() -> {
					status.setText("Export failed: " + t);
					updateEnabledStates();
					JOptionPane.showMessageDialog(this, "Export failed:\n" + t, TITLE, JOptionPane.ERROR_MESSAGE);
				});
			}
		});
	}

	private void exportDone(File folder, List<String> written) {
		updateEnabledStates();
		status.setText("Exported " + written.size() + " file(s) to " + folder);
		StringBuilder msg = new StringBuilder("Exported to " + folder + ":\n\n");
		for (String path : written) {
			msg.append("    ").append(new File(path).getName()).append('\n');
		}
		boolean canOpen = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
		Object[] options = canOpen ? new Object[] { "Open folder", "OK" } : new Object[] { "OK" };
		int choice = JOptionPane.showOptionDialog(this, msg.toString(), TITLE, JOptionPane.DEFAULT_OPTION,
				JOptionPane.INFORMATION_MESSAGE, null, options, options[options.length - 1]);
		if (canOpen && choice == 0) {
			try {
				Desktop.getDesktop().open(folder);
			} catch (IOException e) {
				status.setText("Can't open " + folder + ": " + e.getMessage());
			}
		}
	}

	private void openBatch(List<File> files) {
		final ConversionSettings settings;
		try {
			settings = readSettings();
		} catch (IllegalArgumentException e) {
			JOptionPane.showMessageDialog(this, "Excluded chars: " + e.getMessage(), TITLE,
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (settings.formats.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Select at least one export format on the right first.", TITLE,
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<String> labels = new ArrayList<>();
		for (int i = 0; i < FORMATS.length; i++) {
			if (settings.formats.contains(FORMATS[i])) {
				labels.add(FORMAT_LABELS[i]);
			}
		}
		new BatchDialog(this, settings, String.join(", ", labels), worker, prefs, files).setVisible(true);
	}

	private void copyBasic() {
		if (lastPetscii == null) {
			return;
		}
		String code = String.join("\r\n", lastPetscii.data.getCode()) + "\r\n";
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
		status.setText("BASIC program copied to the clipboard (" + lastPetscii.data.getCode().size() + " lines)");
	}

	private void installDropAndPaste(JComponent target) {
		TransferHandler handler = new TransferHandler() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
						|| support.isDataFlavorSupported(DataFlavor.imageFlavor);
			}

			@Override
			public boolean importData(TransferSupport support) {
				return importTransferable(support.getTransferable());
			}
		};
		target.setTransferHandler(handler);
		sourcePanel.setTransferHandler(handler);
		resultPanel.setTransferHandler(handler);

		target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "pasteImage");
		target.getActionMap().put("pasteImage", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (getFocusOwner() instanceof JTextField) {
					((JTextField) getFocusOwner()).paste();
					return;
				}
				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				if (!importTransferable(clipboard.getContents(null))) {
					status.setText("The clipboard doesn't contain an image");
				}
			}
		});
	}

	private boolean importTransferable(Transferable t) {
		if (t == null) {
			return false;
		}
		try {
			if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				@SuppressWarnings("unchecked")
				List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
				if (files.size() == 1 && !files.get(0).isDirectory()) {
					open(files.get(0));
					return true;
				}
				if (!files.isEmpty()) {
					// Several files or a folder are a job for the batch conversion
					SwingUtilities.invokeLater(() -> openBatch(files));
					return true;
				}
			}
			if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
				open(saveAsTempFile(img));
				return true;
			}
		} catch (Exception e) {
			status.setText("Can't import: " + e.getMessage());
		}
		return false;
	}

	/**
	 * The converter works on files, so pasted images are written into a temp
	 * folder first.
	 */
	private File saveAsTempFile(Image img) throws IOException {
		BufferedImage rgb = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		g.drawImage(img, 0, 0, Color.BLACK, null);
		g.dispose();
		File dir = new File(System.getProperty("java.io.tmpdir"), "petsciiator");
		dir.mkdirs();
		File file = new File(dir, "pasted.png");
		ImageIO.write(rgb, "png", file);
		return file;
	}

	private static boolean isTempFile(File file) {
		try {
			File tmp = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();
			return file.getCanonicalPath().startsWith(tmp.getPath());
		} catch (IOException e) {
			return false;
		}
	}

	private static JComponent titled(JComponent comp, String title) {
		JPanel panel = new JPanel(new BorderLayout());
		JLabel label = new JLabel(title);
		label.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
		panel.add(label, BorderLayout.NORTH);
		panel.add(comp, BorderLayout.CENTER);
		panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
		return panel;
	}

	private List<Image> createIcons() {
		List<Image> icons = new ArrayList<>();
		for (String size : new String[] { "16", "32", "48", "256" }) {
			try {
				Image img = ImageIO.read(PetsciiatorApp.class.getResource("/icon_" + size + ".png"));
				if (img != null) {
					icons.add(img);
				}
			} catch (Exception e) {
				// No icon then
			}
		}
		return icons;
	}

	/**
	 * Helper for building the label/control rows of the settings panel.
	 */
	private static class Row {
		private final JPanel panel;
		private int y = 0;

		Row(JPanel panel) {
			this.panel = panel;
		}

		void heading(String text) {
			JLabel label = new JLabel(text.toUpperCase(Locale.ENGLISH));
			label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = y++;
			c.gridwidth = 2;
			c.anchor = GridBagConstraints.WEST;
			c.insets = new Insets(y == 1 ? 0 : 14, 0, 6, 0);
			panel.add(label, c);
		}

		void add(String labelText, JComponent comp, String tooltip) {
			GridBagConstraints c = new GridBagConstraints();
			c.gridy = y++;
			c.insets = new Insets(2, 0, 2, 0);
			c.anchor = GridBagConstraints.WEST;
			if (!labelText.isEmpty()) {
				c.gridx = 0;
				c.insets = new Insets(2, 0, 2, 10);
				JLabel label = new JLabel(labelText);
				label.setToolTipText(tooltip);
				panel.add(label, c);
				c.insets = new Insets(2, 0, 2, 0);
			}
			c.gridx = 1;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			if (labelText.isEmpty()) {
				c.gridx = 0;
				c.gridwidth = 2;
			}
			if (tooltip != null) {
				comp.setToolTipText(tooltip);
			}
			panel.add(comp, c);
		}

		void finish() {
			GridBagConstraints c = new GridBagConstraints();
			c.gridy = y;
			c.weighty = 1;
			panel.add(new JPanel(), c);
		}
	}
}
