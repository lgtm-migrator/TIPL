/**
 *
 */
package tipl.util;

import tipl.blocks.ITIPLBlock;
import tipl.util.ArgumentList.ArgumentCallback;
import tipl.util.ArgumentList.RangedArgument;

import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * A class to render an argument list as a dialog (based on the GenericDialog of ImageJ)
 *
 * @author mader
 */
public class ArgumentDialog implements ArgumentList.iOptionProcessor,
        ITIPLDialog.DialogInteraction {
    /**
     * A Gui for the SGEJob console
     *
     * @param className
     * @param taskArgs
     */
    public static void SGEJob(final String className, final ArgumentParser taskArgs) {
        ArgumentParser sgeArgs = TIPLGlobal.activeParser("");
        // just for getting the arguments
        SGEJob.runAsJob("", sgeArgs,
                "sge:");
        ArgumentDialog aDialog = ArgumentDialog.newDialog(sgeArgs, "Sun Grid Engine Arguments",
                "Set the appropriate parameters Sun Grid Engine Submission");
        ArgumentParser sgeFinalArgs = aDialog.scrapeDialog();

        SGEJob jobToRun = SGEJob.runAsJob(className, taskArgs.appendToList(sgeFinalArgs
                .subArguments("sge:execute", true)), "sge:");
        jobToRun.submit();
    }

    public String getKey(String keyName) {
        return controls.get(keyName).getValueAsString();
    }

    public void setKey(String keyName, String newValue) {
        controls.get(keyName).setValueFromString(newValue);
    }

    public static boolean showDialogs = !TIPLGlobal.isHeadless();
    final protected ArgumentList coreList;
    final protected ITIPLDialog g;
    final private LinkedHashMap<String, IArgumentBasedControl> controls = new
            LinkedHashMap<String, IArgumentBasedControl>();

    /**
     * Prepare the call-backs for the dialog correctly
     *
     * @param inList
     * @param title
     * @param helpText
     * @return
     */
    public static ArgumentDialog newDialog(final ArgumentList inList, final String title,
                                           final String helpText) {
        final ArgumentDialog outDialog = new ArgumentDialog(inList, title, helpText, true);
        outDialog.g.addDisposalTasks(new Runnable() {
            @Override
            public void run() {
                outDialog.shutdownFunctions();
            }
        });
        return outDialog;
    }

    /**
     * Adds the parameters to an existing dialog object
     *
     * @param parentDialog the dialog object
     * @param inList       the parameters to add
     * @param helpText     help text (if any) to add
     * @return
     */
    public static ArgumentDialog appendDialog(final ITIPLDialog parentDialog,
                                              final ArgumentList inList,
                                              final String helpText) {
        final ArgumentDialog outDialog = new ArgumentDialog(parentDialog, inList, helpText);
        outDialog.g.addDisposalTasks(new Runnable() {
            @Override
            public void run() {
                outDialog.shutdownFunctions();
            }
        });
        return outDialog;
    }

    private ArgumentDialog(final ArgumentList inList, final String title,
                           final String helpText, boolean newLayer) {
        coreList = inList;
        g = new IJDialog(title);
        //if(newLayer) g.createNewLayer("Global Settings");
        if (helpText.length() > 0) g.addMessage(helpText, "", "red");
        inList.processOptions(this);
        g.pack();

        if (showDialogs) g.showDialog();

    }

    private ArgumentDialog(final ITIPLDialog parent, final ArgumentList inList,
                           final String helpText) {
        coreList = inList;
        g = parent;
        if (helpText.length() > 0) g.addMessage(helpText, "", "red");
        inList.processOptions(this);
        if (showDialogs) g.showDialog();
    }

    public static <T extends ITIPLBlock> ArgumentParser GUIBlock(final T blockToRun) {
        return GUIBlock(blockToRun, TIPLGlobal.activeParser(new String[]{}));
    }

    /**
     * Turn a TIPLBlock into a nice GUI (returning the exact same block)
     *
     * @param blockToRun the intialized block
     * @return the block with setParameter run
     */
    public static <T extends ITIPLBlock> ArgumentParser GUIBlock(final T blockToRun,
                                                                 final ArgumentParser args) {
        ArgumentParser p = blockToRun.setParameter(args);
        final ArgumentDialog guiArgs = ArgumentDialog.newDialog(args,
                blockToRun.toString(), blockToRun.getInfo().getDesc());

        p = guiArgs.scrapeDialog();
        System.out.println(p);
        return blockToRun.setParameter(p);
    }

    protected ITIPLDialog.GUIControl addD3Control(final String cName, final D3float cStat,
                                                  final String helpText) {
        boolean oldPreventWrapping = g.getWrapping();
        g.setWrapping(true);
        final ITIPLDialog.GUIControl x = addTextControl(cName + ".x", cStat.x, helpText);
        final ITIPLDialog.GUIControl y = addTextControl(cName + ".y", cStat.y, "");
        final ITIPLDialog.GUIControl z = addTextControl(cName + ".z", cStat.z, "");
        g.setWrapping(oldPreventWrapping);
        return new ITIPLDialog.GUIControl() {

            @Override
            public String getValueAsString() {
                return x.getValueAsString() + "," + y.getValueAsString() + "," +
                        "" + z.getValueAsString();
            }

            @Override
            public void setValueCallback(ArgumentCallback iv) {
                x.setValueCallback(iv);
                y.setValueCallback(iv);
                z.setValueCallback(iv);
            }

            @Override
            public void setValueFromString(String newValue) {
                D3float floatVal = ArgumentList.d3fparse.valueOf(newValue);
                x.setValueFromString("" + floatVal.x);
                y.setValueFromString("" + floatVal.y);
                z.setValueFromString("" + floatVal.z);
            }

        };
    }

    protected ITIPLDialog.GUIControl addTextControl(final String cName,
                                                    final Object cValue, final String helpText) {
        final ITIPLDialog.GUIControl f = g.appendStringField(cName, helpText, cValue.toString());
        return f;
    }

    /**
     * Allow the controls to store the original argument
     *
     * @author mader
     */
    public static interface IArgumentBasedControl extends ITIPLDialog.GUIControl {
        /**
         * @return the argument value stored in the control
         */
        public ArgumentList.Argument getArgument();

    }


    /**
     * A basic model for the argument based controls
     *
     * @author mader
     */
    public static class ArgumentBasedControl implements IArgumentBasedControl {
        final private String cName;
        final private ArgumentList.Argument cArg;
        final private ITIPLDialog.GUIControl guiC;

        public ArgumentBasedControl(final String inName, final ITIPLDialog.GUIControl wrapIt,
                                    final ArgumentList.Argument curArgument) {
            this.cName = inName;
            this.guiC = wrapIt;
            this.cArg = curArgument;
            if (guiC == null)
                throw new IllegalArgumentException("Cannot intialize null object:" + cName + " " +
                        "gui control is not present" + cArg);

        }

        @Override
        public String getValueAsString() {
            if (guiC == null)
                throw new IllegalArgumentException(cName + " gui control is not present" + cArg);
            return guiC.getValueAsString();
        }

        @Override
        public void setValueFromString(String newValue) {
            if (guiC == null)
                throw new IllegalArgumentException(cName + " gui control is not present" + cArg);
            guiC.setValueFromString(newValue);
        }

        @Override
        public void setValueCallback(ArgumentCallback iv) {
            if (guiC == null)
                throw new IllegalArgumentException(cName + " gui control is not present" + cArg);
            guiC.setValueCallback(iv);
        }

        @Override
        public ArgumentList.Argument getArgument() {
            return cArg;
        }

    }

    protected ITIPLDialog.GUIControl getControl(final ArgumentList.Argument cArgument) {
        final String fName = cArgument.getName();
        final String cHelp = cArgument.getHelpText();
        final Object cValue = cArgument.getValue();
        if (cArgument instanceof RangedArgument<?>) {
            final RangedArgument rArg = (RangedArgument<?>) cArgument;
            if (cValue instanceof Double) {
                final double minValue = (Double) rArg.minVal;
                final double maxValue = (Double) rArg.maxVal;
                return g.appendSlider(fName, cHelp, minValue, maxValue,
                        (maxValue + minValue) / 2);
            }
            if (cValue instanceof Integer) {
                final int minValue = (Integer) rArg.minVal;
                final int maxValue = (Integer) rArg.maxVal;
                return g.appendSlider(fName, cHelp, minValue, maxValue,
                        (maxValue + minValue) / 2);
            }
        } else if (cArgument instanceof ArgumentList.MultipleChoiceArgument) {
            final ArgumentList.MultipleChoiceArgument mca = (ArgumentList.MultipleChoiceArgument)
                    cArgument;
            return g.addChoice(fName, cHelp, mca.acceptableAnswers, cArgument.getValueAsString());
        } else if (cValue instanceof Double) {
            return g.appendNumericField(fName, cHelp, (Double) cValue,
                    3);
        } else if (cValue instanceof Integer) {
            return g.appendNumericField(fName, cHelp, (Integer) cValue, 0);
        } else if (cValue instanceof Boolean) {
            final boolean cStat = (Boolean) cValue;
            final ITIPLDialog.GUIControl cChecks = g.appendCheckbox(fName, cHelp, cStat);
            cChecks.setValueCallback(cArgument.getCallback());
            return cChecks;
        } else if (cValue instanceof D3float) {
            final D3float cStat = (D3float) cValue;
            return addD3Control(fName, cStat, cHelp);
        } else if (cValue instanceof TypedPath) {
            return g.appendPathField(fName, cHelp, (TypedPath) cValue);
        } else {
            return g.appendStringField(fName, cHelp, cArgument.getValueAsString());
        }
        throw new IllegalArgumentException(fName + " control should not be null " + cArgument);

    }

    // from the option parser code
    @Override
    public void process(final ArgumentList.Argument cArgument) {
        final String cName = cArgument.getName();
        ITIPLDialog.GUIControl guiC = getControl(cArgument);
        controls.put(cName, new ArgumentBasedControl(cName, guiC, cArgument));
    }

    @Override
    public void setLayer(String currentLayer) {
        g.createNewLayer(currentLayer);
    }

    public synchronized void waitOnDialog() {
        while (!properlyClosed) {
            try {
                Thread.currentThread().sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("waiting for dialog:" + e);
            }
        }
        if (g.wasCanceled())
            throw new IllegalArgumentException(this + " was cancelled cannot continue!");
    }

    private String[] isdString = null;

    /**
     * Gets all the values and puts them into a command line string
     *
     * @return
     */
    private void intScrapeDialogAsString() {
        String curArgs = "";

        for (final String objName : controls.keySet())
            curArgs += " -" + objName + "="
                    + controls.get(objName).getValueAsString();
        final String[] outArgs = curArgs.split(" ");
        isdString = Arrays.copyOfRange(outArgs, 1, outArgs.length);
    }

    private ArgumentParser isdParser;

    /**
     * Gets all of the values and puts them into an argumentparser object
     *
     * @return
     */
    private void intScrapeDialog() {
        ArgumentParser newAp = new ArgumentParser(coreList);
        for (final String objName : controls.keySet()) {
            IArgumentBasedControl aControl = controls.get(objName);
            // make a copy of the argument but insert the value from the textbox / control
            newAp.putArg(objName, aControl.getArgument().cloneWithNewValue(aControl
                    .getValueAsString()));
        }
        isdParser = newAp;
    }

    protected boolean properlyClosed = false;

    protected void shutdownFunctions() {
        intScrapeDialog();
        intScrapeDialogAsString();
        properlyClosed = true;
    }

    public String[] scrapeDialogAsString() {
        waitOnDialog();
        if (isdString == null)
            throw new IllegalArgumentException(this + ":scrapeDialogAsString is null, ");
        return isdString;
    }

    public ArgumentParser scrapeDialog() {
        waitOnDialog();
        if (isdParser == null) throw new IllegalArgumentException(this + ":scrapeDialog is null, ");
        return isdParser;
    }

    public void show() {
        g.showDialog();
    }

}
