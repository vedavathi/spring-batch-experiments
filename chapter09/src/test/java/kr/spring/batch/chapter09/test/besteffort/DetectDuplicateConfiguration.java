package kr.spring.batch.chapter09.test.besteffort;

import kr.spring.batch.chapter09.batch.DuplicateOrderItemProcessor;
import kr.spring.batch.chapter09.batch.InventoryOrderWriter;
import kr.spring.batch.chapter09.domain.Order;
import kr.spring.batch.chapter09.repository.OrderRepository;
import kr.spring.batch.chapter09.test.AbstractJobConfiguration;
import kr.spring.batch.chapter09.test.JpaHSqlConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.jmx.QueueViewMBean;
import org.apache.activemq.command.ActiveMQQueue;
import org.mockito.Mockito;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.jms.JmsItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jmx.access.MBeanProxyFactoryBean;

import javax.jms.Message;
import javax.management.MalformedObjectNameException;
import javax.persistence.EntityManagerFactory;

/**
 * kr.spring.batch.chapter09.test.besteffort.DetectDuplicateConfiguration
 *
 * @author 배성혁 sunghyouk.bae@gmail.com
 * @since 13. 8. 15. 오후 10:43
 */
@Slf4j
@Configuration
@EnableBatchProcessing
@EnableJpaRepositories(basePackageClasses = { OrderRepository.class })
@Import({ JpaHSqlConfiguration.class })
public class DetectDuplicateConfiguration extends AbstractJobConfiguration {

	@Autowired
	EntityManagerFactory emf;

	@Bean
	public Job updateInventoryJob() {
		Step step = stepBuilders.get("updateInventoryStep")
		                        .<Message, Order>chunk(5).readerIsTransactionalQueue()
		                        .reader(orderReader())
		                        .processor(orderProcessor())
		                        .writer(orderWriter())
		                        .build();
		return jobBuilders.get("updateInventoryJob")
		                  .start(step)
		                  .build();
	}

	@Bean
	public Job updateInventoryJobWithMockReader() {
		Step step = stepBuilders.get("updateInventoryStepWithMockReader")
		                        .<Message, Order>chunk(5).readerIsTransactionalQueue()
		                        .reader(mockReader())
		                        .processor(orderProcessor())
		                        .writer(orderWriter())
		                        .build();
		return jobBuilders.get("updateInventoryJobWithMockReader")
		                  .start(step)
		                  .build();
	}

	@Bean
	public JmsItemReader<Message> orderReader() {
		JmsItemReader<Message> reader = new JmsItemReader<Message>();
		reader.setJmsTemplate(jmsTemplate());
		reader.setItemType(javax.jms.Message.class);

		return reader;
	}

	@Bean
	public DuplicateOrderItemProcessor orderProcessor() {
		return new DuplicateOrderItemProcessor();
	}

	@Bean
	public InventoryOrderWriter orderWriter() {
		return new InventoryOrderWriter();
	}

	@Bean
	@SuppressWarnings("unchecked")
	public ItemReader<Message> mockReader() {
		return (ItemReader<Message>) Mockito.mock(ItemReader.class);
	}

	@Bean
	public JmsTemplate jmsTemplate() {
		JmsTemplate jms = new JmsTemplate();
		jms.setConnectionFactory(connectionFactory());
		jms.setDefaultDestination(orderQueue());
		jms.setReceiveTimeout(100L);
		jms.setSessionTransacted(true);

		return jms;
	}


	@Bean
	public CachingConnectionFactory connectionFactory() {
		CachingConnectionFactory ccf = new CachingConnectionFactory();
		ccf.setTargetConnectionFactory(new ActiveMQConnectionFactory("vm://embedded?broker.persistent=false"));
		return ccf;
	}


	@Bean
	public ActiveMQQueue orderQueue() {
		return new ActiveMQQueue("spring.batch.queue.order");
	}

	@Bean
	public QueueViewMBean orderQueueView() {
		MBeanProxyFactoryBean bean = new MBeanProxyFactoryBean();
		bean.setProxyInterface(QueueViewMBean.class);
		try {
			bean.setObjectName("org.apache.activemq:BrokerName=embedded,Type=Queue,Destination=spring.batch.queue.order");
			bean.afterPropertiesSet();
		} catch (MalformedObjectNameException e) {
			log.error("에러", e);
		}
		return (QueueViewMBean) bean.getObject();
	}
}