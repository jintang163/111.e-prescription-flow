export const RX_STATUS = {
  DRAFT: '草稿', SUBMITTED: '已提交', REVIEWING: '审核中', REJECTED: '已驳回',
  AMENDMENT_REQUESTED: '待补正', EFFECTIVE: '已生效待派药', DISPATCHING: '派药中',
  DISPATCHED: '药店已受理', FULFILLING: '配药中', DISPENSED: '已配药',
  READY_FOR_PICKUP: '待取药', PICKED_UP: '已取药', TRANSFER_FAILED: '流转异常', CANCELLED: '已取消'
}
export const FULFILLMENT_STATUS = {
  STOCK_CHECKING: '库存校验中', STOCK_HELD: '已预留药品', ORDER_PLACED: '订单已下发',
  PHARMACY_ACCEPTED: '药店已受理', DISPENSING: '配药中', DISPENSED: '已配齐',
  DELIVERING: '配送中', READY_FOR_PICKUP: '待取药', PICKED_UP: '已取药',
  REJECTED: '缺货/拒绝', CANCELLED: '已取消'
}
export const statusText = s => RX_STATUS[s] || s || ''
export const fulfillmentText = s => FULFILLMENT_STATUS[s] || s || ''
export const statusClass = s => ({
  EFFECTIVE: 'success', PICKED_UP: 'success', READY_FOR_PICKUP: 'success',
  DISPATCHED: 'primary', DISPENSING: 'primary', DISPATCHING: 'primary', DISPENSED: 'primary',
  REVIEWING: 'warning', SUBMITTED: 'warning', AMENDMENT_REQUESTED: 'warning',
  REJECTED: 'danger', TRANSFER_FAILED: 'danger'
}[s] || 'info')
