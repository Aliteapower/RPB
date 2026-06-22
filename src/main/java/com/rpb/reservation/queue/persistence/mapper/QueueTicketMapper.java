package com.rpb.reservation.queue.persistence.mapper;

import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.persistence.entity.QueueTicketEntity;

public interface QueueTicketMapper {

    QueueTicket toDomain(QueueTicketEntity entity);

    QueueTicketEntity toEntity(QueueTicket domain);
}
