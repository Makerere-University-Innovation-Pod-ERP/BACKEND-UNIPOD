package org.unipod.unipoderpbackend.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.config.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
