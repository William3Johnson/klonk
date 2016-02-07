package org.tmotte.klonk.windows.popup;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.JWindow;
import org.tmotte.common.swang.CurrentOS;
import org.tmotte.common.swang.GridBug;
import org.tmotte.common.swang.KeyMapper;
import org.tmotte.common.text.StackTracer;
import org.tmotte.klonk.config.PopupInfo;
import org.tmotte.klonk.config.msg.Setter;
import org.tmotte.klonk.config.option.FontOptions;

/**
 * Always has one button - OK - and a message. Can display
 * larger, more complex messages unlike YesNoCancel.
 */
public class KAlert implements Setter<String> {

  // DI:
  private PopupInfo pInfo;
  private FontOptions fontOptions;

  // Controls:
  private JDialog win;
  private JTextPane errorLabel;
  private JEditorPane sizer;
  private JButton ok;
  private Setter<Throwable> errorHandler;
  int baseSizerHeight;
  int baseLabelHeight;

  // State:
  private boolean initialized=false;

  /////////////////////
  // PUBLIC METHODS: //
  /////////////////////

  public KAlert(PopupInfo pInfo, FontOptions fontOptions) {
    this.pInfo=pInfo;
    this.fontOptions=fontOptions;
    pInfo.addFontListener(fo -> setFont(fo));
  }
  /** Implements Setter interface */
  public @Override void set(String message){
    show(message.toString());
  }
  public Setter<Throwable> getErrorHandler(){
    if (errorHandler==null)
      errorHandler=new Setter<Throwable>(){
        public @Override void set(Throwable error) {
          fail(error);
        }
      };
    return errorHandler;
  }
  public void show(String message) {
    init();
    //Warning: JEditorPane does not wrap words properly, so this is STILL
    //an approximation.
    sizer.setPreferredSize(null);
    sizer.setSize(new Dimension(300,1));
    sizer.setText(message);
    Dimension sized=sizer.getPreferredSize();
    if (sized.height<baseSizerHeight) {
      errorLabel.setPreferredSize(null);
      errorLabel.setText(message);
    }
    else {
      sizer.setSize(500, 10);
      sizer.setText(message);
      Dimension d=sizer.getPreferredSize();
      d.height=d.height+(d.height/baseSizerHeight);
      errorLabel.setPreferredSize(d);
      errorLabel.setText(message);
    }
    win.pack();
    ok.requestFocusInWindow();

    Point pt=pInfo.parentFrame.getLocation();
    win.setLocation(pt.x+20, pt.y+20);
    win.setVisible(true);
    win.toFront();
  }

  public void fail(Throwable e) {
    init();
    show(null, e);
  }

  //////////////////////
  // PRIVATE METHODS: //
  //////////////////////

  private void init() {
    if (!initialized){
      create();
      layout();
      listen();
      initialized=true;
    }
  }
  private void create() {
    win=new JDialog(pInfo.parentFrame, true);

    errorLabel=new JTextPane();
    errorLabel.setEditable(false); // as before
    errorLabel.setBorder(null);
    errorLabel.setOpaque(false);

    sizer=new JEditorPane();
    errorLabel.setText("ABCDEFG\naaa\n\neee\neifif");
    sizer.setText(errorLabel.getText());
    baseSizerHeight=sizer.getPreferredSize().height;
    baseLabelHeight=errorLabel.getPreferredSize().height;

    ok=new JButton("OK");
  }
  private void layout() {
    GridBug gb=new GridBug(win.getContentPane());
    gb.insets.left=10;gb.insets.right=10;
    gb.insets.top=10; gb.insets.bottom=10;
    gb.gridXY(0).weightXY(1);
    gb.fill=gb.BOTH;
    gb.addY(errorLabel);
    gb.weightXY(0);
    gb.fill=gb.NONE;
    gb.insets.top=5;
    gb.insets.bottom=10;
    gb.addY(ok);

    setFont(fontOptions);
  }
  private void listen() {
    Action actions=new AbstractAction() {
      public void actionPerformed(ActionEvent event) {
        win.setVisible(false);
      }
    };
    ok.addActionListener(actions);
    ok.setMnemonic(KeyEvent.VK_K);
    pInfo.currentOS.fixEnterKey(ok, actions);
    KeyMapper.easyCancel(ok, actions);
  }

  private void show(String message, Throwable e) {
    if (message==null)
      message="Whoops... ";
    show(getAlert(message, e));
  }
  private String getAlert(String message, Throwable e) {
    return message+"\n\n"+StackTracer.getStackTrace(e).replaceAll("\t", "    ");
  }
  private void setFont(FontOptions fo) {
    this.fontOptions=fo;
    if (win!=null){
      fontOptions.getControlsFont().set(win);
      win.pack();
    }
  }


  ///////////
  // TEST: //
  ///////////

  public static void main(String[] args) throws Exception {
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        try {
          PopupTestContext ptc=new PopupTestContext();
          KAlert ka=new KAlert(ptc.getPopupInfo(), ptc.getFontOptions());
          ka.show("Small warning thing okay.");
          ka.show("Small warning thing okay but larger.");
          ka.show("Small warning thing okay but it's just a bit larger than before.");
          try {throwTest();} catch (Exception f) {ka.fail(f);}
          ka.show("This is simply the most weird thing anybody's seen in a while, in these parts."
                 +" I'm not sure what you folks are into but you won't be dissatisfied, I "
                 +"assure you");
          ka.show(
            "I dislike you and everything and whatever and so on and howdy and man oh man am I tired "
           +"and this is going to be such a gigantically long sentence that it has no business fitting "
           +"in a window like this it's just too big and it goes on forever keep trying it's almost "
           +"long enough ok now you're cooking just a little more lordy that's enough. Blah blah blah blah"
           +"blah blah blah.\n\n"
           +"Continuing with even more text about nothing in particular, yet something easily understood, "
           +"or perhaps only in theory so, having been written by someone of questionable mental integrity "
           +"and perspective who is not to be trusted with writing sentences of such length or really "
           +"any sentences at all, not to mention sentences that could be misinterpreted as say, a declaration "
           +"of war upon a sovereign nation, should the author happen to be president or secretary of state "
           +"or some other high-ranking position in a heavily armed nation-state foolish enough to elect "
           +"or appoint him. Let's hope that doesn't happen."
          );
          ka.show("I dislike you");
          ka.fail(new RuntimeException("I had a smoochy smooch"));
          return;
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }
  private static void throwTest() {
    new Runnable() {
      public void run() {
        new Runnable() {
          public void run() {
            new Runnable() {
              public void run() {
                new Runnable() {
                  public void run() {throw new RuntimeException("hork");}
                }.run();
              }
            }.run();
          }
        }.run();
      }
    }.run();
  }
}