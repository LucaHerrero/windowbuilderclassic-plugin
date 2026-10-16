package com.herreromarcos.idea.windowbuilderclassic;

/** Formulare wie aus den Wizards des WindowBuilder, als Testeingaben. */
final class Forms {
    static final String FRAME = """
            import java.awt.BorderLayout;
            import java.awt.EventQueue;

            import javax.swing.JFrame;
            import javax.swing.JPanel;
            import javax.swing.border.EmptyBorder;

            public class Form extends JFrame {
                private static final long serialVersionUID = 1L;
                private JPanel contentPane;

                public static void main(String[] args) {
                    EventQueue.invokeLater(() -> new Form().setVisible(true));
                }

                public Form() {
                    setTitle("Hello");
                    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    setBounds(100, 100, 450, 300);
                    contentPane = new JPanel();
                    contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

                    setContentPane(contentPane);
                    contentPane.setLayout(%s);
                }
            }
            """;

    static String frame(String layout) {
        return FRAME.formatted(layout);
    }

    static final String DIALOG = """
            import java.awt.BorderLayout;
            import java.awt.FlowLayout;

            import javax.swing.JButton;
            import javax.swing.JDialog;
            import javax.swing.JPanel;
            import javax.swing.border.EmptyBorder;

            public class Form extends JDialog {
                private final JPanel contentPanel = new JPanel();

                public Form() {
                    setBounds(100, 100, 450, 300);
                    getContentPane().setLayout(new BorderLayout());
                    contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
                    getContentPane().add(contentPanel, BorderLayout.CENTER);
                    {
                        JPanel buttonPane = new JPanel();
                        buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
                        getContentPane().add(buttonPane, BorderLayout.SOUTH);
                        {
                            JButton okButton = new JButton("OK");
                            okButton.setActionCommand("OK");
                            buttonPane.add(okButton);
                            getRootPane().setDefaultButton(okButton);
                        }
                        {
                            JButton cancelButton = new JButton("Cancel");
                            cancelButton.setActionCommand("Cancel");
                            buttonPane.add(cancelButton);
                        }
                    }
                }
            }
            """;

    static final String APPLICATION_WINDOW = """
            import java.awt.EventQueue;

            import javax.swing.JFrame;
            import javax.swing.JLabel;

            public class Form {
                private JFrame frame;

                public static void main(String[] args) {
                    EventQueue.invokeLater(() -> new Form().frame.setVisible(true));
                }

                public Form() {
                    initialize();
                }

                private void initialize() {
                    frame = new JFrame();
                    frame.setBounds(100, 100, 450, 300);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    JLabel lblHello = new JLabel("Hello");
                    frame.getContentPane().add(lblHello);
                }
            }
            """;

    static final String PANEL = """
            import javax.swing.JButton;
            import javax.swing.JPanel;

            public class Form extends JPanel {
                public Form() {
                    JButton btnOne = new JButton("One");
                    add(btnOne);
                    JButton btnTwo = new JButton("Two");
                    add(btnTwo);
                }
            }
            """;

    private Forms() {
    }
}
