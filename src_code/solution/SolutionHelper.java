package solution;

import global.GlobalConst;
import global.SystemDefs;

public class SolutionHelper {
    public static void InitializeDatabase(String dbName, Integer numberOfPages , Integer numberOfBuffers, String replacementPolicy) {
        new SystemDefs(dbName, numberOfPages, numberOfBuffers, replacementPolicy);
    }
}
