package ai.vector.db;

import ai.common.db.HikariDS;
import ai.common.pojo.UserRagSetting;
import ai.utils.AiGlobal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VectorSettingsDao {
    static {
        try {
            Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
            conn.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public List<UserRagSetting> getUserRagVector(String category, String userId) throws SQLException {
        List<UserRagSetting> result = new ArrayList<>();
        String sql = "select id, user_id, file_type, category, chunk_size, temperature from user_rag_settings where category = ? and user_id = ?";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserRagSetting userRagSetting = new UserRagSetting();
                userRagSetting.setId(rs.getInt(1));
                userRagSetting.setUserId(rs.getString(2));
                userRagSetting.setFileType(rs.getString(3));
                userRagSetting.setCategory(rs.getString(4));
                userRagSetting.setChunkSize(rs.getInt(5));
                userRagSetting.setTemperature(rs.getDouble(6));
                result.add(userRagSetting);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

}
