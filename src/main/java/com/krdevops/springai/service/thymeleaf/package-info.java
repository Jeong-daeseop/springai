/**
 * I-2: 기존 JSP·Controller·VO를 안전하게 읽고(I-2B) 구조화된 증거로 추출한 뒤(I-2C)
 * {@code ThymeleafBindingContract}로 조립하는 서비스(I-2E).
 *
 * <p>Thymeleaf HTML 생성(I-4)·Responsive 변환과 Project Apply(I-5)는 포함하지 않는다.
 * {@code ThymeleafGenerationOrchestrationService}(향후 I-4~I-5)만 이 패키지의 서비스를
 * 호출하며, Figma {@code service.figma} 패키지는 이 패키지를 직접 호출하지 않는다(§3.4).
 */
package com.krdevops.springai.service.thymeleaf;
