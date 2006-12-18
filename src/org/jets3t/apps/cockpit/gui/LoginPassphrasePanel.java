/*
 * jets3t : Java Extra-Tasty S3 Toolkit (for Amazon S3 online storage service)
 * This is a java.net project, see https://jets3t.dev.java.net/
 * 
 * Copyright 2006 James Murty
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.jets3t.apps.cockpit.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.jets3t.gui.HyperlinkActivatedListener;
import org.jets3t.gui.JHtmlLabel;

public class LoginPassphrasePanel extends JPanel {
    private static final long serialVersionUID = -5554177389537270280L;

    private final Insets insetsDefault = new Insets(3, 5, 3, 5);
    
    private ActionListener actionListener = null;
    private HyperlinkActivatedListener hyperlinkListener = null;
    private JTextField passphraseTextField = null;
    private JPasswordField passwordPasswordField = null;

    public LoginPassphrasePanel(ActionListener actionListener, HyperlinkActivatedListener hyperlinkListener) {
        super(new GridBagLayout());
        this.actionListener = actionListener;
        this.hyperlinkListener = hyperlinkListener;
        
        initGui();
    }
    
    private void initGui() {
        // Textual information.
        String descriptionText = 
            "<html><center>Your AWS Credentials are stored in an encrypted object in your S3 account. " +
            "To access your credentials you must provide your passphrase and password.</center></html>";
        String passphraseLabelText = 
            "<html><b>Passphrase</b></html>";
        String passphraseDescriptionText = 
            "<html><font size=\"-2\">An easy to remember phrase of 6 characters or more that is unlikely " +
            "to be used by anyone else.</font></html>";
        String passwordLabelText = 
            "<html><b>Password</b></html>";
        String passwordDescriptionText =
            "<html><font size=\"-2\">A password of at least 6 characters.</font></html>";
        
        // Components.
        JHtmlLabel descriptionLabel = new JHtmlLabel(descriptionText, hyperlinkListener);
        descriptionLabel.setHorizontalAlignment(JLabel.CENTER);        
        JHtmlLabel passphraseLabel = new JHtmlLabel(passphraseLabelText, hyperlinkListener);
        passphraseTextField = new JTextField();
        passphraseTextField.setName("LoginPassphrasePanel.Passphrase");
        passphraseTextField.addActionListener(actionListener);
        JHtmlLabel passphraseDescriptionLabel = new JHtmlLabel(passphraseDescriptionText, hyperlinkListener);
        JHtmlLabel passwordLabel = new JHtmlLabel(passwordLabelText, hyperlinkListener);
        passwordPasswordField = new JPasswordField();
        passwordPasswordField.setName("LoginPassphrasePanel.Password");
        passwordPasswordField.addActionListener(actionListener);
        JHtmlLabel passwordDescriptionLabel = new JHtmlLabel(passwordDescriptionText, hyperlinkListener);
        
        int row = 0;
        add(descriptionLabel, new GridBagConstraints(0, row++,
            1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, insetsDefault, 0, 0));
        add(passphraseLabel, new GridBagConstraints(0, row++,
            1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
        add(passphraseTextField, new GridBagConstraints(0, row++,
            1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
        add(passphraseDescriptionLabel, new GridBagConstraints(0, row++,
            1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
        add(passwordLabel, new GridBagConstraints(0, row++,
            1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
        add(passwordPasswordField, new GridBagConstraints(0, row++,
            1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
        add(passwordDescriptionLabel, new GridBagConstraints(0, row++,
            1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
        
        // Padder.
        add(new JLabel(), new GridBagConstraints(0, row++,
            1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, insetsDefault, 0, 0));
    }    
    
    public String getPassphrase() {
        return passphraseTextField.getText();
    }
    
    public String getPassword() {
        return new String(passwordPasswordField.getPassword());
    }
    
}
