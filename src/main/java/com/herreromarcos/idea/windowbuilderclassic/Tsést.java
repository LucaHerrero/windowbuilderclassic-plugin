package com.herreromarcos.idea.windowbuilderclassic;

import java.awt.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class Tsést extends JFrame {

    private static final long serialVersionUID = 1L;
    private JPanel contentPane;

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    Tsést frame = new Tsést();
                    frame.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Create the frame.
     */
    public Tsést() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 450, 300);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

        setContentPane(contentPane);
        GridBagLayout gbl_contentPane = new GridBagLayout();
        gbl_contentPane.columnWidths = new int[]{0, 0, 0, 0};
        gbl_contentPane.rowHeights = new int[]{0, 0};
        gbl_contentPane.columnWeights = new double[]{0.0, 0.0, 1.0, Double.MIN_VALUE};
        gbl_contentPane.rowWeights = new double[]{0.0, Double.MIN_VALUE};
        contentPane.setLayout(gbl_contentPane);
        JLabel lblNewLabel = new JLabel("New label");
        GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
        gbc_lblNewLabel.gridx = 0;
        gbc_lblNewLabel.gridy = 0;
        contentPane.add(lblNewLabel, gbc_lblNewLabel);
        JLabel lblNewLabel_1 = new JLabel("New label");
        GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
        gbc_lblNewLabel_1.gridx = 1;
        gbc_lblNewLabel_1.gridy = 0;
        contentPane.add(lblNewLabel_1, gbc_lblNewLabel_1);
        JPanel panel = new JPanel();
        GridBagConstraints gbc_panel = new GridBagConstraints();
        gbc_panel.anchor = GridBagConstraints.EAST;
        gbc_panel.gridx = 2;
        gbc_panel.gridy = 0;
        contentPane.add(panel, gbc_panel);
    }

}
