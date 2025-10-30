package zju.cst.aces.util.yate;

import com.mkonst.analysis.JavaClassContainer;
import com.mkonst.helpers.*;
import com.mkonst.runners.YateJavaRunner;
import com.mkonst.types.YateResponse;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.config.YateConfig;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.PromptInfo;

import java.util.ArrayList;

public class YateFixer {
    private final Config config;
    private final String repositoryPath;

    public YateFixer(Config config) {
        this.config = config;
        this.repositoryPath = config.getProject().getBasedir().getAbsolutePath();
    }

    public YateResponse fixGeneratedTest(String fullClassName, ClassInfo classInfo, PromptInfo promptInfo) {
        String testClassName = getTestClassName(promptInfo);

        // Locate class under test path (used by YATE to generate and analyze related files)
        String cutPath = YateJavaUtils.INSTANCE.getClassFileFromPackage(repositoryPath, fullClassName);

        // Create a YATE Class Container to hold the CUT info
        JavaClassContainer cutContainer = new JavaClassContainer(classInfo.className, classInfo.compilationUnitCode);
        cutContainer.getPaths().setCut(cutPath);

        // Create a YATE Test class container to hold the info of the generated test class
        JavaClassContainer testContainer = new JavaClassContainer(testClassName, promptInfo.getUnitTest());
        testContainer.setPathsFromCut(cutContainer);
        testContainer.toTestFile();

        // Create a YateResponse object, to allow YATE to append modifications between LLM-interactions
        YateResponse yateResponse = new YateResponse(testContainer, new ArrayList<>(), false);

        // Initialize a new runner to invoke YATE components
        YateJavaRunner runner = new YateJavaRunner(
                repositoryPath,
                config.yate.useOracleFixing,
                config.yate.outputDirectory,
                config.yate.modelName
        );

        // Invoke YATE's compilation fixing component
        runner.fixGeneratedTestClass(cutContainer, yateResponse);
        yateResponse.getTestClassContainer().toTestFile();

        // Invoke YATE's oracle fixing component (if enabled)
        if (config.yate.useOracleFixing) {
            runner.fixOraclesInTestClass(yateResponse);
            yateResponse.getTestClassContainer().toTestFile();
        }

        // Move generated test class to output directory (if enabled)
        if (config.yate.outputDirectory != null) {
            YateUtils.INSTANCE.moveGeneratedTestClass(yateResponse.getTestClassContainer(), config.yate.outputDirectory);
        }

        // Has Changed flag will hold whether the YateResponse has any failures or not
        yateResponse.setHasChanges(hasFailed(yateResponse));
        return yateResponse;
    }

    private String getTestClassName(PromptInfo promptInfo) {
        String[] testClassSplit = promptInfo.fullTestName.split("\\.");

        return testClassSplit[testClassSplit.length - 1];
    }

    private boolean hasFailed(YateResponse yateResponse) {
        try {
            return YateCodeUtils.INSTANCE.countTestMethods(yateResponse.getTestClassContainer()) <= 0;
        } catch (Exception e) {
            YateConsole.INSTANCE.error("An error occurred when counting test methods");
            YateConsole.INSTANCE.error(e.getMessage());

            return true;
        }
    }
}
