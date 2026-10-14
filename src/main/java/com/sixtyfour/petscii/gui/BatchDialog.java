package com.sixtyfour.petscii.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Converts a list of images with the main window's current settings and export
 * formats.
 */
public class BatchDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	static final String[] EXTENSIONS = { "jpg", "jpeg", "png", "gif", "bmp", "webp" };

	private final ConversionSettings settings;
	private final ExecutorService worker;
	private final Preferences prefs;

	private final DefaultListModel<File> files = new DefaultListModel<>();
	private final JList<File> fileList = new JList<>(files);
	private final JTextField targetField = new JTextField();
	private final JProgressBar progress = new JProgressBar();
	private final JLabel progressLabel = new JLabel(" ");
	private final JTextArea log = new JTextArea(6, 40);
	private final JButton startButton = new JButton("Convert");
	private final JButton closeButton = new JButton("Close");
	private final List<JButton> listButtons = new ArrayList<>();

	private volatile boolean cancelled = false;
	private boolean running = false;

	public BatchDialog(JFrame owner, ConversionSettings settings, String formatSummary, ExecutorService worker,
			Preferences prefs, Collection<File> initialFiles) {
		super(owner, "Batch conversion", true);
		this.settings = settings;
		this.worker = worker;
		this.prefs = prefs;
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				close();
			}
		});

		JPanel content = new JPanel(new GridBagLayout());
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 6, 0);

		content.add(new JLabel("Images to convert (drop files or folders here):"), c);

		c.gridy++;
		c.gridwidth = 1;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		fileList.setVisibleRowCount(10);
		fileList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				File file = (File) value;
				setText(file.getName() + "   —   " + file.getParent());
				return this;
			}
		});
		content.add(new JScrollPane(fileList), c);

		JPanel listButtonPanel = new JPanel(new GridBagLayout());
		GridBagConstraints bc = new GridBagConstraints();
		bc.gridx = 0;
		bc.fill = GridBagConstraints.HORIZONTAL;
		bc.insets = new Insets(0, 6, 4, 0);
		listButtonPanel.add(listButton("Add files...", e -> addFiles()), bc);
		listButtonPanel.add(listButton("Add folder...", e -> addFolder()), bc);
		listButtonPanel.add(listButton("Remove", e -> {
			int[] sel = fileList.getSelectedIndices();
			for (int i = sel.length - 1; i >= 0; i--) {
				files.remove(sel[i]);
			}
			updateState();
		}), bc);
		listButtonPanel.add(listButton("Clear", e -> {
			files.clear();
			updateState();
		}), bc);
		bc.weighty = 1;
		listButtonPanel.add(new JPanel(), bc);
		c.gridx = 1;
		c.weightx = 0;
		c.fill = GridBagConstraints.VERTICAL;
		content.add(listButtonPanel, c);

		c.gridx = 0;
		c.gridy++;
		c.gridwidth = 2;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(10, 0, 4, 0);
		content.add(new JLabel("Output folder (existing files with the same names are replaced):"), c);

		c.gridy++;
		c.gridwidth = 1;
		c.insets = new Insets(0, 0, 0, 0);
		targetField.setText(prefs.get("batchDir", prefs.get("exportDir", "")));
		content.add(targetField, c);
		c.gridx = 1;
		c.weightx = 0;
		JButton browse = listButton("Browse...", e -> chooseTarget());
		content.add(browse, c);

		c.gridx = 0;
		c.gridy++;
		c.gridwidth = 2;
		c.weightx = 1;
		c.insets = new Insets(10, 0, 0, 0);
		JLabel formatsLabel = new JLabel("Uses the current settings. Export formats: " + formatSummary);
		formatsLabel.setForeground(Color.GRAY);
		content.add(formatsLabel, c);

		c.gridy++;
		c.insets = new Insets(10, 0, 4, 0);
		progress.setStringPainted(true);
		progress.setString("");
		content.add(progress, c);
		c.gridy++;
		c.insets = new Insets(0, 0, 4, 0);
		content.add(progressLabel, c);

		c.gridy++;
		c.weighty = 0.6;
		c.fill = GridBagConstraints.BOTH;
		log.setEditable(false);
		log.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		content.add(new JScrollPane(log), c);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		startButton.addActionListener(e -> start());
		closeButton.addActionListener(e -> close());
		buttons.add(startButton);
		buttons.add(closeButton);
		c.gridy++;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, 0, 0, 0);
		content.add(buttons, c);

		TransferHandler dropHandler = new TransferHandler() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean canImport(TransferSupport support) {
				return !running && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override
			@SuppressWarnings("unchecked")
			public boolean importData(TransferSupport support) {
				try {
					addAll((List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
					return true;
				} catch (Exception e) {
					return false;
				}
			}
		};
		content.setTransferHandler(dropHandler);
		fileList.setTransferHandler(dropHandler);

		setContentPane(content);
		getRootPane().setDefaultButton(startButton);
		if (initialFiles != null) {
			addAll(initialFiles);
		}
		updateState();

		setSize(720, 600);
		setMinimumSize(new Dimension(560, 460));
		setLocationRelativeTo(owner);
	}

	static boolean isImage(File file) {
		String name = file.getName().toLowerCase(Locale.ENGLISH);
		for (String ext : EXTENSIONS) {
			if (name.endsWith("." + ext)) {
				return true;
			}
		}
		return false;
	}

	private JButton listButton(String text, java.awt.event.ActionListener listener) {
		JButton button = new JButton(text);
		button.addActionListener(listener);
		listButtons.add(button);
		return button;
	}

	/**
	 * Adds files and the images directly inside of folders (like the command line
	 * version, sub folders aren't included).
	 */
	private void addAll(Collection<File> toAdd) {
		Set<File> present = new LinkedHashSet<>();
		for (int i = 0; i < files.size(); i++) {
			present.add(files.get(i));
		}
		for (File file : toAdd) {
			List<File> candidates = new ArrayList<>();
			if (file.isDirectory()) {
				File[] content = file.listFiles();
				if (content != null) {
					Arrays.sort(content);
					candidates.addAll(Arrays.asList(content));
				}
			} else {
				candidates.add(file);
			}
			for (File candidate : candidates) {
				if (candidate.isFile() && isImage(candidate) && present.add(candidate)) {
					files.addElement(candidate);
				}
			}
		}
		updateState();
	}

	private void addFiles() {
		JFileChooser chooser = new JFileChooser(prefs.get("openDir", null));
		chooser.setDialogTitle("Add images");
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG, GIF, BMP, WebP)", EXTENSIONS));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			addAll(Arrays.asList(chooser.getSelectedFiles()));
		}
	}

	private void addFolder() {
		JFileChooser chooser = new JFileChooser(prefs.get("openDir", null));
		chooser.setDialogTitle("Add all images in a folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showDialog(this, "Add folder") == JFileChooser.APPROVE_OPTION) {
			int before = files.size();
			addAll(Arrays.asList(chooser.getSelectedFile()));
			if (files.size() == before) {
				JOptionPane.showMessageDialog(this, "No images found in that folder.", getTitle(),
						JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private void chooseTarget() {
		String start = targetField.getText().trim();
		JFileChooser chooser = new JFileChooser(start.isEmpty() ? null : start);
		chooser.setDialogTitle("Output folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().getPath());
		}
	}

	private void updateState() {
		startButton.setEnabled(!running && !files.isEmpty());
		for (JButton button : listButtons) {
			button.setEnabled(!running);
		}
		targetField.setEnabled(!running);
		closeButton.setText(running ? "Cancel" : "Close");
		if (!running) {
			progressLabel.setText(files.size() + " image(s)");
		}
	}

	private void start() {
		String targetText = targetField.getText().trim();
		if (targetText.isEmpty()) {
			chooseTarget();
			targetText = targetField.getText().trim();
			if (targetText.isEmpty()) {
				return;
			}
		}
		final File target = new File(targetText);
		if (target.exists() && !target.isDirectory()) {
			JOptionPane.showMessageDialog(this, target + " isn't a folder.", getTitle(), JOptionPane.ERROR_MESSAGE);
			return;
		}

		final List<File> todo = new ArrayList<>();
		for (int i = 0; i < files.size(); i++) {
			todo.add(files.get(i));
		}

		// Outputs are named after the image without its extension, so a.png and
		// a.jpg would overwrite each other
		Map<String, Integer> names = new HashMap<>();
		List<String> clashes = new ArrayList<>();
		for (File file : todo) {
			String name = file.getName();
			int dot = name.lastIndexOf('.');
			String base = (dot > 0 ? name.substring(0, dot) : name).toLowerCase(Locale.ENGLISH);
			int count = names.merge(base, 1, Integer::sum);
			if (count == 2) {
				clashes.add(base);
			}
		}
		if (!clashes.isEmpty()) {
			int answer = JOptionPane.showConfirmDialog(this,
					"Some images share a name (" + String.join(", ", clashes.subList(0, Math.min(5, clashes.size())))
							+ (clashes.size() > 5 ? ", ..." : "")
							+ "),\nso their output files will overwrite each other. Convert anyway?",
					getTitle(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.OK_OPTION) {
				return;
			}
		}

		target.mkdirs();
		prefs.put("batchDir", target.getPath());

		running = true;
		cancelled = false;
		log.setText("");
		progress.setMaximum(todo.size());
		progress.setValue(0);
		progress.setString("0 / " + todo.size());
		updateState();

		worker.submit(() -> {
			int ok = 0;
			int failed = 0;
			int fileCount = 0;
			long start = System.currentTimeMillis();
			for (int i = 0; i < todo.size() && !cancelled; i++) {
				File file = todo.get(i);
				final int index = i;
				SwingUtilities.invokeLater(() -> progressLabel.setText("Converting " + file.getName() + "..."));
				try {
					List<String> written = Converter.export(file, settings, target);
					ok++;
					fileCount += written.size();
					appendLog("OK      " + file.getName());
				} catch (Throwable t) {
					failed++;
					appendLog("FAILED  " + file.getName() + ": " + t.getMessage());
				}
				SwingUtilities.invokeLater(() -> {
					progress.setValue(index + 1);
					progress.setString((index + 1) + " / " + todo.size());
				});
			}
			final String summary = (cancelled ? "Cancelled. " : "Done. ") + ok + " converted"
					+ (failed > 0 ? ", " + failed + " failed" : "") + ", " + fileCount + " file(s) written in "
					+ ((System.currentTimeMillis() - start + 500) / 1000) + " s";
			SwingUtilities.invokeLater(() -> finished(summary, target));
		});
	}

	private void appendLog(String line) {
		SwingUtilities.invokeLater(() -> {
			log.append(line + "\n");
			log.setCaretPosition(log.getDocument().getLength());
		});
	}

	private void finished(String summary, File target) {
		running = false;
		updateState();
		progressLabel.setText(summary);
		appendLog(summary);
		boolean canOpen = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
		if (canOpen) {
			Object[] options = { "Open folder", "OK" };
			int choice = JOptionPane.showOptionDialog(this, summary, getTitle(), JOptionPane.DEFAULT_OPTION,
					JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
			if (choice == 0) {
				try {
					Desktop.getDesktop().open(target);
				} catch (IOException e) {
					appendLog("Can't open " + target + ": " + e.getMessage());
				}
			}
		}
	}

	private void close() {
		if (running) {
			cancelled = true;
			progressLabel.setText("Cancelling after the current image...");
			return;
		}
		dispose();
	}
}
