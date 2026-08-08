package com.mokatest.platform.demos.debug;

import com.mokatest.platform.demos.domain.ui.uiEnum.*;
import com.mokatest.platform.demos.domain.ui.uiEnum.debug.ExecuteType;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.result.BaseStepResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.Node.StepTreeNode;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ForStep;
import com.mokatest.platform.demos.step.stepImpl.WhileCycleStep;
import com.mokatest.platform.demos.step.stepbuild.StepBuilderFactory;
import com.microsoft.playwright.*;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.ViewportSize;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Slf4j
public abstract class TestExecutionContext {

    // 场景执行类型
    protected ExecuteType executeType;

    // 场景配置
    private SceneSetting sceneSetting;

    /**
     * 场景级环境（UI 场景引用的环境，来自 sceneSetting.apiSceneConfig.environmentId）。
     * 会话初始化时由 DebugSessionManager / TaskManager 解析注入；
     * HTTP/SQL 步骤执行时据此合并 baseUrl、环境 Header/Cookie，SQL 步骤据此解析环境级数据库连接。
     */
    private com.mokatest.platform.demos.api.domain.Environment sceneEnvironment;

    // 调试的场景
    protected final List<StepTreeNode> testSteps = new ArrayList<>();


    // 步骤计数
    private AtomicInteger stepIndex = new AtomicInteger(0);

    protected String sessionId;
    protected final Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected final List<Page> allPages = new CopyOnWriteArrayList<>();
    protected AtomicInteger currentPageIndex = new AtomicInteger(0);
    protected List<StepResult> stepResults = new ArrayList<>();
    protected volatile DebuggerState state = DebuggerState.READY;
    protected final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    protected Thread executionThread;
    protected boolean autoSwitchToNewPages = true;
    protected boolean captureScreenshots = true;
    protected boolean headless = false;

    // 是否继续执行
    protected boolean isContinue = true;

    // 当前的场景id
    private Integer scenarioId;

    // 场景名称
    private String sceneName;

    // iframe相关状态
    protected Frame currentFrame;
    protected final Map<Page, List<Frame>> pageFrames = new ConcurrentHashMap<>();
    protected final Map<Page, Frame> mainFrames = new ConcurrentHashMap<>();
    private Stack<StepResult> completedResultQueue = new Stack<>();

    /**
     * 变量表
     */
    protected final Map<String, Object> variables = new ConcurrentHashMap<>();


    /**
     * 添加测试步骤
     *
     * @param step
     */
    public void addStep(StepTreeNode step) {
        testSteps.add(step);
    }

    // 当前步骤的结果
    private StepResult currentStepResult;

    public TestExecutionContext() {
        playwright = Playwright.create();
        log.info("Playwright实例创建：{}", playwright);
    }


    public void increment() {
        //获取当前的值并自增
        stepIndex.incrementAndGet();
    }

    public Integer getStepIndex() {
        //获取当前的值并自增
        return stepIndex.get();
    }


    protected abstract void executeTest();


    /**
     * 切换到指定索引的页面
     *
     * @param index
     */
    protected void switchToPage(int index) {
        if (index >= 0 && index < allPages.size()) {
            currentPageIndex.set(index);
            Page page = allPages.get(index);
            if (page == null || page.isClosed() || !browser.isConnected() || context == null || browser == null || playwright == null) {
                return;
            }
            page.bringToFront();
            currentFrame = mainFrames.get(page); // 重置为页面的主框架
        }
    }

    /**
     * 设置页面监听器
     *
     * @param page
     */
    public void setupPageListeners(Page page) {
        // 初始化框架列表
        pageFrames.put(page, new CopyOnWriteArrayList<>());
        pageFrames.get(page).add(page.mainFrame());
        mainFrames.put(page, page.mainFrame());
        currentFrame = page.mainFrame();

        // 监听框架添加事件
        page.onFrameAttached(frame -> {
//            System.out.println("\nFrame attached: " + frame.url());
            pageFrames.get(page).add(frame);
        });

        // 监听框架导航事件：新开页面/跨进程导航后 Chromium 会替换主框架对象，
        // 需同步刷新 mainFrames 缓存；若 currentFrame 已分离（或就是旧主框架）则一并切换到新主框架，
        // 避免后置等待、元素定位等逻辑拿到已分离的旧框架
        page.onFrameNavigated(frame -> {
            if (frame.parentFrame() == null) {
                Frame oldMain = mainFrames.put(page, frame);
                try {
                    if (currentFrame == null || currentFrame.equals(oldMain)
                            || (page.equals(currentFrame.page()) && currentFrame.isDetached())) {
                        currentFrame = frame;
                    }
                } catch (Exception e) {
                    currentFrame = frame;
                }
            }
        });

        // 监听框架分离事件
        page.onFrameDetached(frame -> {
//            System.out.println("\nFrame detached: " + frame.url());
            pageFrames.get(page).remove(frame);

            // 如果当前框架被移除，切换到主框架
            if (frame.equals(currentFrame)) {
                currentFrame = mainFrames.get(page);
//                System.out.println("Switched to main frame");
            }
        });

        // 监听新页面创建事件
        page.onPopup(popup -> {
//            System.out.println("\nPopup opened: " + popup.url());
            initNewPage(popup);
        });

        // 监听页面关闭事件
        page.onClose(p -> {
//            System.out.println("\nPage closed: " + p.url());
            Page currentPage = getCurrentPage();
            // 判断是否是当前活动页面
            boolean isActivePage = p.equals(currentPage);
            // 判断是否需要更改活动页面的index，如果关闭页面在活动页面之前，就得减一。否则不处理
            boolean isPreviousPage = allPages.indexOf(p) < currentPageIndex.get();


            // 如果关闭的是当前活动的页面，切换到上一个页面，如果超过一个页面，切换到第一个页面

            if (!allPages.isEmpty()) {
                allPages.remove(p);
                pageFrames.remove(p);
                mainFrames.remove(p);
                // 移除之后，还需要再判断一次
                if (isActivePage) {
                    // 关闭的如果是当前活动页面，则切换到下一个页面
                    // 如果下一个页面index超过size，则切换到上一个页面
                    int closeIndex = getCurrentPageIndex().get();
                    int switchIndex = Math.min(closeIndex, allPages.size() - 1);
                    switchToPage(switchIndex);
                } else {
                    // 如果关闭的不是当前活动页面，就不用切换tab
                    // 需要更新一次当前页面的index
                    if (isPreviousPage) {
                        // 判断关闭的页面是在当前活动页面的前，还是后
                        currentPageIndex.decrementAndGet();
                    }
                }

            }
        });

        // 控制台日志监听
/*            page.onConsoleMessage(msg ->
                System.out.println("[" + getFrameIndicator(page, msg.frame()) + "] Console: " + msg.text()));*/

        // 网络请求监听
/*//        page.onRequest(request ->
//                System.out.println("[" + getFrameIndicator(page, request.frame()) + "] Request: " + request.method() + " " + request.url())
//        );*/

        // 网络响应监听
    /*    page.onResponse(response ->
                System.out.println("[" + getFrameIndicator(page, response.frame()) + "] Response: " + response.status() + " " + response.url()));*/
    }


    /**
     * 获取框架标识符
     *
     * @param page
     * @param frame
     * @return
     */
    protected String getFrameIndicator(Page page, Frame frame) {
        if (frame == null) return page.url();
        if (frame.equals(page.mainFrame())) return "main@" + page.url();

        int index = pageFrames.get(page).indexOf(frame);
        if (index != -1) {
            return "frame" + index + "@" + frame.url();
        }
        return "unknown-frame@" + frame.url();
    }


    public void printHelp() {
        System.out.println("\nAvailable commands:");
        System.out.println("  n          - Next step (step over)");
        System.out.println("  c          - Continue execution");
        System.out.println("  s          - Capture screenshot");
        System.out.println("  d          - Show current DOM");
        System.out.println("  lp         - List all open pages");
        System.out.println("  sp <num>   - Switch to page by index");
        System.out.println("  su <str>   - Switch to page containing URL");
        System.out.println("  cp         - Close current page");
        System.out.println("  ap         - Add new blank page");
        System.out.println("  np <url>   - Open URL in new page");
        System.out.println("  lf         - List frames in current page");
        System.out.println("  sf <num>   - Switch to frame by index");
        System.out.println("  sfn <name> - Switch to frame by name/URL");
        System.out.println("  sm         - Switch to main frame");
        System.out.println("  q          - Quit debugging");
    }

    // 获取当前活动页面
    public Page getCurrentPage() {
        if (allPages.isEmpty()) return null;
        return allPages.get(currentPageIndex.get());
    }

    // 获取当前活动框架
    public Frame getCurrentFrame() {
        // 缓存框架已分离（跨进程导航会替换主框架对象）时，回退到当前页面的活动主框架
        if (currentFrame != null && !currentFrame.isDetached()) {
            return currentFrame;
        }
        Page page = getCurrentPage();
        return page != null ? page.mainFrame() : null;
    }


    protected BrowserContext createContext() {
        Browser.NewContextOptions options = initNewContextOptions();
        if (this.browser == null) {
            startBrowser();
        }
        BrowserContext browserContext = this.browser.newContext(options);
        log.info("场景：{} 的上下文创建成功", this.getSceneName());
        browserContext.onClose(context -> {
            log.info("场景：{} 的上下文关闭", this.getSceneName());
        });
        return browserContext;
    }


    /**
     * 启动浏览器
     */
    private void startBrowser() {
        BrowserType.LaunchOptions launchOptions = initLaunchOptions();
        BrowserType browserType = setBrowserType(launchOptions);
        this.browser = browserType.launch(launchOptions);
        log.info("场景：{} 的browser创建成功", this.getSceneName());
        this.browser.onDisconnected(browser -> {
            log.info("浏览器已断开连接");
        });
    }


    /**
     * 初始化浏览器启动参数
     *
     * @return
     */
    private BrowserType.LaunchOptions initLaunchOptions() {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions();
        setHeadless(launchOptions);
        setBrowserWindowSize(launchOptions);
        // todo 其他内容待做
//        launchOptions.setTimeout(30000); // 浏览器启动超时时间（毫秒）
//        launchOptions.setExecutablePath("/path/to/chrome"); // 设置浏览器可执行文件路径
        return launchOptions;
    }

    /**
     * 设置浏览器窗口大小
     *
     * @param launchOptions
     */
    private void setBrowserWindowSize(BrowserType.LaunchOptions launchOptions) {
        List<String> argumentList = new ArrayList<>();
        // 创建浏览器上下文
        WindowModel windowModel = WindowModel.valueOf(this.getSceneSetting().getSceneBrowserConfig().getWindowMode().toString());
        switch (windowModel) {
            case MAXIMIZE -> {
                // 判断浏览器类型（非无头模式 + 浏览器是火狐，才使用该参数）
                if (com.mokatest.platform.demos.domain.ui.uiEnum.BrowserType.FIREFOX.equals(
                        com.mokatest.platform.demos.domain.ui.uiEnum.BrowserType.valueOf(this.getSceneSetting().getSceneBrowserConfig().getBrowserType().toString()))
                        && BrowserRunningType.NORMAL.equals(BrowserRunningType.valueOf(this.getSceneSetting().getSceneBrowserConfig().getRunningType().toString()))
                ) {
                    argumentList.add("--kiosk");
                } else {
                    argumentList.add("--start-maximized");
                }
            }
            case CUSTOMSIZE -> {
                String[] size = this.getSceneSetting().getSceneBrowserConfig().getWindowSize().split("x");
                argumentList.add(String.format("--window-size=%s,%s", size[0], size[1]));
            }
            default -> {
            }
        }
        launchOptions.setArgs(argumentList);
    }


    /**
     * 初始化浏览器上下文参数
     *
     * @return
     */
    private Browser.NewContextOptions initNewContextOptions() {
        Browser.NewContextOptions options = new Browser.NewContextOptions();
        setViewportSize(options);
        setDeviceType(options);
        setLocale(options);
        if (this instanceof PlaywrightPlanSession) {
            options.setStorageState(((PlaywrightPlanSession) this).getAuthContext());
        }
        // setGeolocation(options);
        // todo 设置 下列配置后续根据情况待做
        // options.setIgnoreHTTPSErrors(true);         // 忽略 HTTPS 错误
        // options.setJavaScriptEnabled(true);         // 是否启用 JavaScript
        // options.setLocale("zh-CN");                 // 语言设置
        return options;
    }


    /**
     * 设置浏览器有头/无头模式
     *
     * @param launchOptions
     * @return
     */
    private void setHeadless(BrowserType.LaunchOptions launchOptions) {
        BrowserRunningType browserRunningType =
                BrowserRunningType.valueOf(this.getSceneSetting().getSceneBrowserConfig().getRunningType().toString());
        switch (browserRunningType) {
            case HEADLESS -> launchOptions.setHeadless(true);
            case NORMAL -> launchOptions.setHeadless(false);
        }
    }


    /**
     * 设置浏览器类型
     *
     * @param launchOptions
     * @return
     */
    private BrowserType setBrowserType(BrowserType.LaunchOptions launchOptions) {
        com.mokatest.platform.demos.domain.ui.uiEnum.BrowserType browserTypeConfig =
                com.mokatest.platform.demos.domain.ui.uiEnum.BrowserType.valueOf(this.getSceneSetting().getSceneBrowserConfig().getBrowserType().toString());
        BrowserType browserType = null;
        switch (browserTypeConfig) {
            case CHROME -> browserType = this.playwright.chromium();
            case FIREFOX -> browserType = this.playwright.firefox();
            case EDGE -> {
                launchOptions.setChannel("msedge");
                browserType = this.playwright.chromium();
            }
            case IE -> browserType = this.playwright.webkit();
            case SAFARI -> browserType = this.playwright.webkit();
            default -> throw new RuntimeException("不支持的浏览器类型：" + browserType);
        }
        return browserType;
    }

    /**
     * 设置页面视图大小
     *
     * @param options
     */
    private void setViewportSize(Browser.NewContextOptions options) {
        // 设置设备类型
        DeviceType deviceType = this.getSceneSetting().getSceneBrowserConfig().getDeviceType();
        if (deviceType != null && DeviceType.MOBILE.equals(DeviceType.valueOf(deviceType.toString()))) {
            options.setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    .setViewportSize(360, 640)
                    .setDeviceScaleFactor(2)
                    .setIsMobile(true)
                    .setHasTouch(true);
        } else {
//            创建浏览器上下文
            WindowModel windowModel = WindowModel.valueOf(this.getSceneSetting().getSceneBrowserConfig().getWindowMode().toString());
            // 重要：最大化的时候必须得设置视图窗口为null，防止浏览器窗口最大化的时候，视图窗口被设置为默认值
            switch (windowModel) {
                case MAXIMIZE -> options.setViewportSize(null);
                case CUSTOMSIZE -> {
                    String[] size = this.getSceneSetting().getSceneBrowserConfig().getWindowSize().split("x");
                    options.setViewportSize(new ViewportSize(Integer.parseInt(size[0]), Integer.parseInt(size[1])));
                }
                default -> {
                }
            }
        }
    }

    /**
     * 设置地理位置
     *
     * @param options
     */
    private void setGeolocation(Browser.NewContextOptions options) {
        // todo 从 getSceneCommonConfig 中获取地理位置信息
        options.setGeolocation(37.77, -122.41);
    }

    /**
     * 设置适配类型（移动端、PC端、Mac）
     *
     * @param options
     */
    private void setDeviceType(Browser.NewContextOptions options) {
        DeviceType deviceType = this.getSceneSetting().getSceneBrowserConfig().getDeviceType();
        if (deviceType != null && DeviceType.MOBILE.equals(DeviceType.valueOf(deviceType.toString()))) {
            options.setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
            options.setIsMobile(true);
        } else {
            options.setIsMobile(false);
        }
    }

    /**
     * 设置语言
     *
     * @param options
     */
    private void setLocale(Browser.NewContextOptions options) {
        // todo 从 getSceneCommonConfig 中获取语言信息,暂时使用中文
        options.setLocale("zh-CN");
    }


    /**
     * 获取当前的步骤结果
     */
    public StepResult getCurrentStepResult() {
        return this.currentStepResult;
    }

    /**
     * 获取当前的通用步骤结果
     *
     * @return
     */
    public BaseStepResult getCurrentCommonStepResult() {
        BaseStepResult baseStepResult = null;
        if (this.getCurrentStepResult().getIsLoop() > 0) {
            // 处于循环状态，就获取循环步骤结果
            baseStepResult =
                    getCurrentStepResult().getIterations().get(this.getCurrentStepResult().getIterations().size());
        } else {
            baseStepResult = getCurrentStepResult().getResult();

        }
        return baseStepResult;
    }


    /**
     * 获取上下文处理策略
     * 空值兜底：场景/步骤配置缺失时，调试会话默认 STOP（失败挂起，与 Setting 构造默认值一致）；
     * 其他执行模式返回 null（维持原「继续执行」语义），避免因配置缺失 NPE 中断整个会话
     *
     * @return
     */
    public StepErrorHandleStrategy getStepErrorHandleStrategy() {
        // 异常处理策略
        // 先获取场景通用的，再获取步骤自身的
        StepErrorHandleStrategy stepErrorHandleStrategy = null;
        if (this.getSceneSetting() != null && this.getSceneSetting().getSetting() != null) {
            stepErrorHandleStrategy = this.getSceneSetting().getSetting().getErrorHandlingStrategy();
        }
        AbstractTestStep currentStep = this.getCurrentStepResult() != null ? this.getCurrentStepResult().getStep() : null;
        if (currentStep != null && currentStep.getSetting() != null && currentStep.getSetting().getIsSetting() != 0
                && currentStep.getSetting().getErrorHandlingStrategy() != null) {
            stepErrorHandleStrategy = currentStep.getSetting().getErrorHandlingStrategy();
        }
        if (stepErrorHandleStrategy == null && this instanceof PlaywrightDebugSession) {
            return StepErrorHandleStrategy.STOP;
        }
        return stepErrorHandleStrategy;
    }


    /**
     * 尝试停止后续执行
     */
    public void tryStopContextContineExecution() {
        StepErrorHandleStrategy stepErrorHandleStrategy = getStepErrorHandleStrategy();
        if (stepErrorHandleStrategy != null && stepErrorHandleStrategy.equals(StepErrorHandleStrategy.STOP)) {
            this.isContinue = false;
        }
    }

    /**
     * 判断是否继续执行
     */
    public boolean contextIsContinue() {
        return this.isContinue;
    }


    /**
     * 初始化步骤结果列表
     *
     * @param testSteps 测试步骤列表
     * @return 步骤结果列表
     */
    protected List<StepResult> initialStepResults(List<StepTreeNode> testSteps) {
        if (CollectionUtils.isEmpty(testSteps)) {
            return new ArrayList<>();
        }
        List<StepResult> results = new ArrayList<>();
        StepBuilderFactory stepBuilderFactory = ApplicationContextHolder.getBean(StepBuilderFactory.class);
        for (StepTreeNode testStep : testSteps) {
            // 构建测试步骤
            AbstractTestStep step = stepBuilderFactory.build(
                    testStep.getStepEntity().getStepType(),
                    testStep.getStepEntity()
            );
            StepResult stepResult = new StepResult(step);
            if (!CollectionUtils.isEmpty(testStep.getChildren())) {
                stepResult.setChildren(new ArrayList<>());
                // 如果当前步骤是循环步骤，设置循环层级
                boolean loopStep = isLoopStep(step);
                recursionBuild(stepResult.getChildren(), testStep.getChildren(), stepBuilderFactory, loopStep ? 1 : 0);
            }
            results.add(stepResult);
        }
        return results;
    }

    protected void recursionBuild(List<StepResult> parentList, List<StepTreeNode> testSteps,
                                  StepBuilderFactory stepBuilderFactory, int loopLevel) {
        if (CollectionUtils.isEmpty(testSteps)) {
            return;
        }
        for (StepTreeNode stepNode : testSteps) {
            // 构建测试步骤
            AbstractTestStep testStep = stepBuilderFactory.build(
                    stepNode.getStepEntity().getStepType(),
                    stepNode.getStepEntity()
            );
            StepResult stepResult = new StepResult(testStep);
            stepResult.setIsLoop(loopLevel);

            // 递归处理子节点
            if (!CollectionUtils.isEmpty(stepNode.getChildren())) {
                stepResult.setChildren(new ArrayList<>());
                // 如果当前是循环步骤，传递增加后的层级；否则传递原层级
                boolean loopStep = isLoopStep(testStep);
                recursionBuild(stepResult.getChildren(), stepNode.getChildren(), stepBuilderFactory,
                        loopStep ? loopLevel + 1 : loopLevel);
            }
            parentList.add(stepResult);
        }
    }

    // 判断是否是循环步骤
    public boolean isLoopStep(AbstractTestStep step) {
        return step instanceof ForStep || step instanceof WhileCycleStep;
    }

    /**
     * 初始化新页面
     */
    public void initNewPage(Page page) {
        allPages.add(page);
        setupPageListeners(page);
        if (autoSwitchToNewPages) {
            switchToPage(allPages.size() - 1);
        }
    }


    public void closeBrowser() {
        if (browser != null && browser.isConnected()) {
            browser.close();
            browser = null;
        }
    }

    public void closeContext() {
        if (context != null && browser != null && browser.isConnected()) {
            context.close();
            context = null;
        }
    }

    public void closePlaywright() {
        if (playwright != null) {
            playwright.close();
        }
    }

    public void closeAllResources() {
        closeContext();
        closeBrowser();
        closePlaywright();
    }


}