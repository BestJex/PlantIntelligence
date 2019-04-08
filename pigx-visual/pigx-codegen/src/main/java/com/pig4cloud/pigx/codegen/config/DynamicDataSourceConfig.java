package com.pig4cloud.pigx.codegen.config;

import com.mysql.cj.jdbc.Driver;
import com.pig4cloud.pigx.codegen.util.DataSourceConstant;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author lengleng
 * @date 2019-03-31
 * <p>
 * 动态数据源切换配置
 */
@Slf4j
@Configuration
@AllArgsConstructor
public class DynamicDataSourceConfig implements TransactionManagementConfigurer {
	private final Map<Object, Object> dataSourceMap = new HashMap<>(8);
	private final DataSourceProperties dataSourceProperties;
	private final StringEncryptor stringEncryptor;

	@Bean("dynamicDataSource")
	public DynamicDataSource dataSource() {
		DynamicDataSource ds = new DynamicDataSource();
		HikariDataSource cads = new HikariDataSource();
		cads.setJdbcUrl(dataSourceProperties.getUrl());
		cads.setDriverClassName(dataSourceProperties.getDriverClassName());
		cads.setUsername(dataSourceProperties.getUsername());
		cads.setPassword(dataSourceProperties.getPassword());
		ds.setDefaultTargetDataSource(cads);
		dataSourceMap.put(0, cads);
		ds.setTargetDataSources(dataSourceMap);
		return ds;
	}

	/**
	 * 组装默认配置的数据源，查询数据库配置
	 */
	@PostConstruct
	public void init() {
		DriverManagerDataSource dds = new DriverManagerDataSource();
		dds.setUrl(dataSourceProperties.getUrl());
		dds.setDriverClassName(dataSourceProperties.getDriverClassName());
		dds.setUsername(dataSourceProperties.getUsername());
		dds.setPassword(dataSourceProperties.getPassword());

		List<Map<String, Object>> dbList = new JdbcTemplate(dds).queryForList(DataSourceConstant.QUERY_DS_SQL);
		log.info("开始 -> 初始化动态数据源");
		Optional.of(dbList).ifPresent(list -> list.forEach(db -> {
			log.info("数据源:{}", db.get(DataSourceConstant.DS_NAME));
			HikariDataSource ds = new HikariDataSource();
			ds.setJdbcUrl(String.valueOf(db.get(DataSourceConstant.DS_JDBC_URL)));
			ds.setDriverClassName(Driver.class.getName());
			ds.setUsername((String) db.get(DataSourceConstant.DS_NAME));

			String decPwd = stringEncryptor.decrypt((String) db.get(DataSourceConstant.DS_USER_PWD));
			ds.setPassword(decPwd);
			dataSourceMap.put(db.get(DataSourceConstant.DS_ROUTE_KEY), ds);
		}));

		log.info("完毕 -> 初始化动态数据源,共计 {} 条", dataSourceMap.size());
	}

	/**
	 * 重新加载数据源配置
	 */
	public Boolean reload() {
		init();
		DynamicDataSource dataSource = dataSource();
		dataSource.setTargetDataSources(dataSourceMap);
		dataSource.afterPropertiesSet();
		return Boolean.FALSE;
	}


	@Bean
	public PlatformTransactionManager txManager() {
		return new DataSourceTransactionManager(dataSource());
	}

	@Override
	public PlatformTransactionManager annotationDrivenTransactionManager() {
		return txManager();
	}

}