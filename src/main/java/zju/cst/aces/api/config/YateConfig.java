package zju.cst.aces.api.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YateConfig {
    public boolean useOracleFixing = false;
    public String modelName = null;
    public String outputDirectory = null;
}
