package otterbk.mcplugin.customexperience;

public class ExperienceInfo {

    protected int startLevel = 0;
    protected int endLevel = 0;
    protected int requireExpr = 0;

    public ExperienceInfo()
    {
    }

    public ExperienceInfo(int startLevel, int endLevel, int requireExpr)
    {
        this();
        this.startLevel = startLevel;
        this.endLevel = endLevel;
        this.requireExpr = requireExpr;
    }

}
