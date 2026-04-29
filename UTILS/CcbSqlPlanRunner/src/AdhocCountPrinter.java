import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public final class AdhocCountPrinter {
    private AdhocCountPrinter() {
    }

    public static void main(String[] args) throws Exception {
        Config config = ConnectionLoader.load(Path.of(args.length > 0 ? args[0] : "config/tnsnames.txt"));
        try (Connection connection = ConnectionLoader.connect(config);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select\n"
                             + "  (select count(*) from ci_td_entry_cha) total_rows,\n"
                             + "  (select count(*) from ci_td_entry_cha where char_type_cd = 'LIC-S4ET') lic_s4et_rows,\n"
                             + "  (select count(*) from ci_td_entry_cha where char_type_cd = 'PCZ-EVT ') pcz_evt_rows,\n"
                             + "  (select count(distinct char_type_cd) from ci_td_entry_cha) distinct_char_types,\n"
                             + "  (select count(*) from ci_wf_proc where wf_proc_tmpl_cd = '13T_PRELAW  ') prelaw_proc_rows,\n"
                             + "  (select count(*)\n"
                             + "     from ci_td_entry_cha cha\n"
                             + "    where cha.char_type_cd = 'PCZ-EVT '\n"
                             + "      and cha.char_val_fk1 in (\n"
                             + "          select p.wf_proc_id\n"
                             + "            from ci_wf_proc p\n"
                             + "           where p.wf_proc_tmpl_cd = '13T_PRELAW  '\n"
                             + "      )) prelaw_pcz_evt_rows,\n"
                             + "  (select count(distinct cha.td_entry_id)\n"
                             + "     from ci_td_entry_cha cha\n"
                             + "    where cha.char_type_cd = 'PCZ-EVT '\n"
                             + "      and cha.char_val_fk1 in (\n"
                             + "          select p.wf_proc_id\n"
                             + "            from ci_wf_proc p\n"
                             + "           where p.wf_proc_tmpl_cd = '13T_PRELAW  '\n"
                             + "      )) prelaw_pcz_evt_td_entries\n"
                             + "from dual")) {
            if (rs.next()) {
                System.out.println("total_rows=" + rs.getLong("total_rows"));
                System.out.println("lic_s4et_rows=" + rs.getLong("lic_s4et_rows"));
                System.out.println("pcz_evt_rows=" + rs.getLong("pcz_evt_rows"));
                System.out.println("distinct_char_types=" + rs.getLong("distinct_char_types"));
                System.out.println("prelaw_proc_rows=" + rs.getLong("prelaw_proc_rows"));
                System.out.println("prelaw_pcz_evt_rows=" + rs.getLong("prelaw_pcz_evt_rows"));
                System.out.println("prelaw_pcz_evt_td_entries=" + rs.getLong("prelaw_pcz_evt_td_entries"));
            }
        }
    }
}
